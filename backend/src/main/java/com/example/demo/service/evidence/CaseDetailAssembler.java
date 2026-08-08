package com.example.demo.service.evidence;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.CaseEvidenceSummaryDto;
import com.example.demo.service.evidence.hls.EvidenceHlsLookupService;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.OrganizationIdResolver;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseDetailAssembler {

    private final CaseEvidencePresentationService caseEvidencePresentationService;
    private final EvidenceHlsLookupService evidenceHlsLookupService;

    public CaseDetailResponse assembleEmptyCase(String caseId, CaseProfile profile, User uploader) {
        Long ownerId = profile.getUploaderId();
        CaseDetailResponse.CaseDetailResponseBuilder builder = CaseDetailResponse.builder()
                .caseId(caseId)
                .caseName(caseId)
                .status("PENDING")
                .createdAt(ApiDateTimeFormatter.formatUtc(profile.getUpdatedAt()))
                .representativeEvidenceId(profile.getRepresentativeEvidenceId())
                .evidences(List.of());
        applyRbacFields(builder, profile, uploader, ownerId);
        return builder.build();
    }

    public CaseDetailResponse assemble(
            User ownerUser,
            String caseId,
            List<Evidence> evidences,
            List<AnalysisRequest> analysisRequests,
            CaseProfile profile,
            User uploader,
            Map<Long, EvidenceHls> hlsByEvidenceId
    ) {
        Map<Long, AnalysisRequest> latestByEvidence = indexLatestRequests(analysisRequests);
        String caseName = evidences.stream()
                .map(Evidence::getCaseName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(caseId);
        LocalDateTime createdAt = evidences.stream()
                .map(Evidence::getUploadedAt)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        Long representativeEvidenceId = caseEvidencePresentationService
                .resolveRepresentativeEvidenceId(ownerUser, caseId, evidences)
                .orElse(null);
        Long ownerId = evidences.isEmpty() ? null : evidences.get(0).getUploaderId();
        List<Evidence> orderedEvidences = caseEvidencePresentationService.orderForDisplay(evidences);
        List<CaseEvidenceSummaryDto> summaries = orderedEvidences.stream()
                .map(evidence -> toCaseEvidenceSummary(
                        evidence,
                        orderedEvidences,
                        latestByEvidence.get(evidence.getEvidenceId()),
                        hlsByEvidenceId
                ))
                .toList();

        CaseDetailResponse.CaseDetailResponseBuilder builder = CaseDetailResponse.builder()
                .caseId(caseId)
                .caseName(caseName)
                .status(aggregateStatus(evidences, latestByEvidence))
                .createdAt(ApiDateTimeFormatter.formatUtc(createdAt))
                .representativeEvidenceId(representativeEvidenceId)
                .evidences(summaries);
        applyRbacFields(builder, profile, uploader, ownerId);
        return builder.build();
    }

    private void applyRbacFields(
            CaseDetailResponse.CaseDetailResponseBuilder builder,
            CaseProfile profile,
            User uploader,
            Long ownerId
    ) {
        Long assigneeId = profile != null && profile.getAssigneeId() != null
                ? profile.getAssigneeId()
                : ownerId;
        builder
                .organizationId(uploader == null
                        ? null
                        : OrganizationIdResolver.resolve(uploader.getOrganizationType()))
                .createdBy(ownerId == null ? null : String.valueOf(ownerId))
                .assigneeId(assigneeId == null ? null : String.valueOf(assigneeId))
                .reviewerId(profile != null && profile.getReviewerId() != null
                        ? String.valueOf(profile.getReviewerId())
                        : null)
                .reviewStatus(profile != null && profile.getReviewStatus() != null
                        ? profile.getReviewStatus().name()
                        : CaseReviewStatus.NONE.name())
                .reviewRequestedAt(profile != null && profile.getReviewRequestedAt() != null
                        ? ApiDateTimeFormatter.formatUtc(profile.getReviewRequestedAt())
                        : null)
                .reviewerComment(profile == null ? null : profile.getReviewerComment());
    }

    public String resolveAggregateStatus(List<Evidence> evidences, List<AnalysisRequest> analysisRequests) {
        return aggregateStatus(evidences, indexLatestRequests(analysisRequests));
    }

    private Map<Long, AnalysisRequest> indexLatestRequests(List<AnalysisRequest> requests) {
        Map<Long, AnalysisRequest> latestByEvidence = new HashMap<>();
        for (AnalysisRequest request : requests) {
            latestByEvidence.putIfAbsent(request.getEvidenceId(), request);
        }
        return latestByEvidence;
    }

    private CaseEvidenceSummaryDto toCaseEvidenceSummary(
            Evidence evidence,
            List<Evidence> caseEvidences,
            AnalysisRequest request,
            Map<Long, EvidenceHls> hlsByEvidenceId
    ) {
        String hlsStatus = resolveHlsStatusName(evidence, hlsByEvidenceId);
        return CaseEvidenceSummaryDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileName(evidence.getFileName())
                .displayLabel(caseEvidencePresentationService.resolveDisplayLabel(evidence, caseEvidences))
                .originalFileName(evidence.getFileName())
                .mediaType(evidence.getFileType().name())
                .analysisStatus(toCaseStatus(request))
                .analysisProgress(resolveAnalysisProgress(request))
                .lifecycleStatus(caseEvidencePresentationService.lifecycleStatusName(evidence))
                .role(caseEvidencePresentationService.roleName(evidence))
                .replacementEvidenceId(evidence.getReplacementEvidenceId())
                .excludedReason(evidence.getExcludedReason())
                .hlsStatus(hlsStatus)
                .build();
    }

    private String resolveHlsStatusName(Evidence evidence, Map<Long, EvidenceHls> hlsByEvidenceId) {
        var status = evidenceHlsLookupService.resolveStatus(
                evidence.getFileType(),
                evidence.getEvidenceId(),
                hlsByEvidenceId
        );
        return status != null ? status.name() : null;
    }

    private String aggregateStatus(List<Evidence> evidences, Map<Long, AnalysisRequest> latestByEvidence) {
        if (evidences.isEmpty()) {
            return "PENDING";
        }
        String result = "COMPLETED";
        for (Evidence evidence : evidences) {
            String status = toCaseStatus(latestByEvidence.get(evidence.getEvidenceId()));
            result = higherPriorityStatus(result, status);
        }
        return result;
    }

    private String higherPriorityStatus(String current, String candidate) {
        Map<String, Integer> order = Map.of(
                "PROCESSING", 0,
                "PENDING", 1,
                "FAILED", 2,
                "COMPLETED", 3
        );
        return order.get(candidate) < order.get(current) ? candidate : current;
    }

    private String toCaseStatus(AnalysisRequest request) {
        if (request == null) {
            return "PENDING";
        }
        return AnalysisStatusMapper.toApiStatus(request.getStatus());
    }

    private Integer resolveAnalysisProgress(AnalysisRequest request) {
        if (request == null) {
            return 0;
        }
        if (request.getStatus() == AnalysisStatus.COMPLETED) {
            return 100;
        }
        return request.getProgressPercent();
    }
}
