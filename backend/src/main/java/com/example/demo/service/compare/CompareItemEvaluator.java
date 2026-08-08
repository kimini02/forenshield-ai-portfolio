package com.example.demo.service.compare;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.CompareItemResult;
import com.example.demo.domain.enums.CompareVerdict;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.dto.compare.CompareSummaryDto;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.FfprobeCompareHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompareItemEvaluator {

    private final BlockchainAnchorService blockchainAnchorService;
    private final ObjectMapper objectMapper;

    public List<CompareItemDto> buildComparisonItems(
            Evidence original,
            EvidenceMetadata originalMetadata,
            String candidateHash,
            long candidateSize,
            Optional<FfprobeCompareHelper.ProbeSnapshot> originalProbe,
            Optional<FfprobeCompareHelper.ProbeSnapshot> candidateProbe
    ) {
        List<CompareItemDto> items = new ArrayList<>();

        items.add(compareValues(
                "SHA256",
                "SHA-256 해시",
                original.getOriginalHashValue(),
                candidateHash
        ));

        items.add(compareValues(
                "FILE_SIZE",
                "파일 크기",
                String.valueOf(original.getFileSize()),
                String.valueOf(candidateSize)
        ));

        Optional<FfprobeCompareHelper.ProbeSnapshot> resolvedOriginalProbe = originalProbe != null
                ? originalProbe
                : Optional.empty();

        items.add(compareOptional(
                "DURATION",
                "영상 길이(초)",
                originalMetadata != null && originalMetadata.getDurationSec() != null
                        ? String.valueOf(originalMetadata.getDurationSec())
                        : resolvedOriginalProbe.map(p -> p.getDurationSec() == null ? null : String.valueOf(p.getDurationSec())).orElse(null),
                candidateProbe.map(p -> p.getDurationSec() == null ? null : String.valueOf(p.getDurationSec())).orElse(null)
        ));

        String originalCodec = codecLabel(resolvedOriginalProbe);
        String candidateCodec = codecLabel(candidateProbe);
        items.add(compareOptional("CODEC", "코덱 정보", originalCodec, candidateCodec));

        String originalTimestamp = originalMetadata != null && originalMetadata.getCapturedAt() != null
                ? ApiDateTimeFormatter.formatUtc(originalMetadata.getCapturedAt())
                : resolvedOriginalProbe.map(FfprobeCompareHelper.ProbeSnapshot::getTimestamp).orElse(null);
        String candidateTimestamp = candidateProbe.map(FfprobeCompareHelper.ProbeSnapshot::getTimestamp).orElse(null);
        items.add(compareOptional("TIMESTAMP", "메타데이터 타임스탬프", originalTimestamp, candidateTimestamp));

        items.add(compareOptional(
                "GOP",
                "GOP 구조",
                resolvedOriginalProbe.map(FfprobeCompareHelper.ProbeSnapshot::getGopFingerprint).orElse(null),
                candidateProbe.map(FfprobeCompareHelper.ProbeSnapshot::getGopFingerprint).orElse(null)
        ));

        items.add(compareOptional(
                "STREAM_CHECKSUM",
                "스트림 식별값",
                resolvedOriginalProbe.map(FfprobeCompareHelper.ProbeSnapshot::getStreamFingerprint).orElse(null),
                candidateProbe.map(FfprobeCompareHelper.ProbeSnapshot::getStreamFingerprint).orElse(null)
        ));

        items.add(compareOptional(
                "BLOCKCHAIN_HASH",
                "등록 해시 대조",
                blockchainAnchorService.findAnchoredEvidenceSubjectHash(original.getEvidenceId()).orElse(null),
                candidateHash
        ));

        return items;
    }

    public CompareSummaryDto summarize(List<CompareItemDto> items) {
        int match = 0;
        int mismatch = 0;
        int skipped = 0;
        for (CompareItemDto item : items) {
            switch (item.getResult()) {
                case MATCH -> match++;
                case MISMATCH -> mismatch++;
                case SKIPPED -> skipped++;
            }
        }
        return CompareSummaryDto.builder()
                .matchCount(match)
                .mismatchCount(mismatch)
                .skippedCount(skipped)
                .build();
    }

    public CompareVerdict determineVerdict(List<CompareItemDto> items, String candidateHash, String originalHash) {
        if (originalHash != null && originalHash.equals(candidateHash)) {
            return CompareVerdict.ORIGINAL_MATCH;
        }
        boolean hasMismatch = items.stream().anyMatch(item -> item.getResult() == CompareItemResult.MISMATCH);
        if (hasMismatch) {
            return CompareVerdict.TAMPERED;
        }
        return CompareVerdict.INCONCLUSIVE;
    }

    private static String codecLabel(Optional<FfprobeCompareHelper.ProbeSnapshot> probe) {
        return probe
                .map(p -> FfprobeCompareHelper.formatCodec(p.getVideoCodec(), p.getAudioCodec()))
                .orElse(null);
    }

    private CompareItemDto compareValues(String key, String label, String originalValue, String candidateValue) {
        CompareItemResult result = originalValue != null && originalValue.equals(candidateValue)
                ? CompareItemResult.MATCH
                : CompareItemResult.MISMATCH;
        return CompareItemDto.builder()
                .itemKey(key)
                .label(label)
                .originalValue(originalValue)
                .candidateValue(candidateValue)
                .result(result)
                .build();
    }

    private CompareItemDto compareOptional(String key, String label, String originalValue, String candidateValue) {
        if (originalValue == null || candidateValue == null) {
            return CompareItemDto.builder()
                    .itemKey(key)
                    .label(label)
                    .originalValue(originalValue)
                    .candidateValue(candidateValue)
                    .result(CompareItemResult.SKIPPED)
                    .build();
        }
        CompareItemResult result = originalValue.equals(candidateValue)
                ? CompareItemResult.MATCH
                : CompareItemResult.MISMATCH;
        return CompareItemDto.builder()
                .itemKey(key)
                .label(label)
                .originalValue(originalValue)
                .candidateValue(candidateValue)
                .result(result)
                .build();
    }
}
