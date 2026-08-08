package com.example.demo.service.compare;

import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.CompareVerdict;
import com.example.demo.dto.compare.CompareBlockchainInfoDto;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.dto.compare.CompareResultResponse;
import com.example.demo.dto.compare.CompareSignatureInfoDto;
import com.example.demo.dto.compare.CompareSummaryDto;
import com.example.demo.dto.compare.CompareVerifyResponse;
import com.example.demo.util.ApiDateTimeFormatter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompareVerificationAssembler {

    private final ObjectMapper objectMapper;

    public CompareVerifyResponse toVerifyResponse(
            CompareVerification saved,
            Evidence original,
            List<CompareItemDto> items,
            CompareSummaryDto summary,
            CompareSignatureInfoDto signature,
            CompareBlockchainInfoDto blockchain
    ) {
        CompareSummaryDto summaryWithLabel = CompareSummaryDto.builder()
                .matchCount(summary.getMatchCount())
                .mismatchCount(summary.getMismatchCount())
                .skippedCount(summary.getSkippedCount())
                .verdictLabel(verdictLabel(saved.getVerdict()))
                .build();

        return CompareVerifyResponse.builder()
                .compareId(saved.getCompareId())
                .originalEvidenceId(saved.getOriginalEvidenceId())
                .candidateFileName(saved.getCandidateFileName())
                .verdict(saved.getVerdict())
                .summary(summaryWithLabel)
                .items(items)
                .signature(signature)
                .blockchain(blockchain)
                .createdAt(ApiDateTimeFormatter.formatUtc(saved.getCreatedAt()))
                .build();
    }

    public CompareResultResponse toResultResponse(
            CompareVerification verification,
            List<CompareItemDto> items,
            CompareSignatureInfoDto signature,
            CompareBlockchainInfoDto blockchain
    ) {
        CompareSummaryDto summary = CompareSummaryDto.builder()
                .matchCount(verification.getMatchCount())
                .mismatchCount(verification.getMismatchCount())
                .skippedCount(verification.getSkippedCount())
                .verdictLabel(verdictLabel(verification.getVerdict()))
                .build();

        return CompareResultResponse.builder()
                .compareId(verification.getCompareId())
                .originalEvidenceId(verification.getOriginalEvidenceId())
                .candidateFileName(verification.getCandidateFileName())
                .verdict(verification.getVerdict())
                .summary(summary)
                .items(items)
                .signature(signature)
                .blockchain(blockchain)
                .createdAt(ApiDateTimeFormatter.formatUtc(verification.getCreatedAt()))
                .build();
    }

    public CompareFileInfoDto toOriginalFileInfo(Evidence evidence) {
        return CompareFileInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileName(evidence.getFileName())
                .fileSize(evidence.getFileSize())
                .sha256(evidence.getOriginalHashValue())
                .caseName(evidence.getCaseName())
                .caseNumber(evidence.getCaseNumber())
                .fileType(evidence.getFileType() != null ? evidence.getFileType().name() : null)
                .mimeType(evidence.getMimeType())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
                .build();
    }

    public CompareFileInfoDto toCandidateFileInfo(CompareVerification verification) {
        return CompareFileInfoDto.builder()
                .compareId(verification.getCompareId())
                .fileName(verification.getCandidateFileName())
                .fileSize(verification.getCandidateFileSize())
                .sha256(verification.getCandidateHash())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(verification.getCreatedAt()))
                .build();
    }

    public String serializeItems(List<CompareItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("비교 결과 직렬화에 실패했습니다.", ex);
        }
    }

    public List<CompareItemDto> deserializeItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("비교 결과 역직렬화에 실패했습니다.", ex);
        }
    }

    public String verdictLabel(CompareVerdict verdict) {
        return switch (verdict) {
            case ORIGINAL_MATCH -> "원본 일치";
            case TAMPERED -> "위변조 의심";
            case INCONCLUSIVE -> "판정 불가";
        };
    }
}
