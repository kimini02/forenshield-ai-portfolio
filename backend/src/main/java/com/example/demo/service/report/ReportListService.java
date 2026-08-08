package com.example.demo.service.report;

import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CompareVerdict;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.report.ReportListPageResponse;
import com.example.demo.dto.report.ReportSummaryDto;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CompareVerificationRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import com.example.demo.util.UserRoleSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportListService {

    private final ReportRepository reportRepository;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final CompareVerificationRepository compareVerificationRepository;

    public ReportListPageResponse listReports(User user, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Report> reportPage = UserRoleSupport.isReviewer(user.getRole())
                ? reportRepository.findIssuedByReviewerAssignment(user.getUserId(), pageable)
                : reportRepository.findByCreatedByOrderByCreatedAtDesc(user.getUserId(), pageable);

        List<Long> evidenceIds = reportPage.getContent().stream()
                .map(Report::getEvidenceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Evidence> evidenceById = evidenceRepository
                .findByEvidenceIdInAndDeletedAtIsNull(evidenceIds)
                .stream()
                .collect(Collectors.toMap(Evidence::getEvidenceId, Function.identity()));

        List<Long> analysisResultIds = reportPage.getContent().stream()
                .map(Report::getAnalysisResultId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, AnalysisResult> analysisById = analysisResultRepository.findByAnalysisResultIdIn(analysisResultIds)
                .stream()
                .collect(Collectors.toMap(AnalysisResult::getAnalysisResultId, Function.identity()));

        List<Long> compareIds = reportPage.getContent().stream()
                .map(Report::getCompareId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, CompareVerification> compareById = compareVerificationRepository.findByCompareIdIn(compareIds)
                .stream()
                .collect(Collectors.toMap(CompareVerification::getCompareId, Function.identity()));

        List<ReportSummaryDto> content = reportPage.getContent().stream()
                .map(report -> toSummary(report, evidenceById, analysisById, compareById))
                .toList();

        return ReportListPageResponse.builder()
                .content(content)
                .page(reportPage.getNumber())
                .size(reportPage.getSize())
                .totalElements(reportPage.getTotalElements())
                .totalPages(reportPage.getTotalPages())
                .build();
    }

    private ReportSummaryDto toSummary(
            Report report,
            Map<Long, Evidence> evidenceById,
            Map<Long, AnalysisResult> analysisById,
            Map<Long, CompareVerification> compareById
    ) {
        Evidence evidence = evidenceById.get(report.getEvidenceId());
        boolean isCompare = report.getCompareId() != null;
        String reportType = isCompare ? "COMPARE" : "ANALYSIS";
        String verdictLabel = resolveVerdictLabel(report, analysisById, compareById);

        return ReportSummaryDto.builder()
                .reportId(report.getReportId())
                .reportType(reportType)
                .evidenceId(report.getEvidenceId())
                .compareId(report.getCompareId())
                .caseId(evidence != null ? EvidenceCaseIdResolver.resolve(evidence) : null)
                .caseName(evidence != null ? evidence.getCaseName() : null)
                .reportFileName(report.getReportFileName())
                .verdictLabel(verdictLabel)
                .createdAt(ApiDateTimeFormatter.formatUtc(report.getCreatedAt()))
                .reportHash(report.getReportHash())
                .publicationStatus(report.getPublicationStatus().name())
                .version(report.getReportVersion())
                .issuedAt(report.getIssuedAt() == null ? null : ApiDateTimeFormatter.formatUtc(report.getIssuedAt()))
                .downloadPath(report.isIssued() ? resolveDownloadPath(report, isCompare) : null)
                .build();
    }

    private String resolveVerdictLabel(
            Report report,
            Map<Long, AnalysisResult> analysisById,
            Map<Long, CompareVerification> compareById
    ) {
        if (report.getCompareId() != null) {
            CompareVerification verification = compareById.get(report.getCompareId());
            if (verification != null && verification.getVerdict() != null) {
                return compareVerdictLabel(verification.getVerdict());
            }
            return null;
        }
        if (report.getAnalysisResultId() != null) {
            AnalysisResult result = analysisById.get(report.getAnalysisResultId());
            if (result != null && result.getRiskLevel() != null) {
                return analysisRiskLabel(result.getRiskLevel());
            }
        }
        return null;
    }

    private String resolveDownloadPath(Report report, boolean isCompare) {
        if (isCompare) {
            return "/api/v1/compare/" + report.getCompareId() + "/reports/pdf";
        }
        return "/api/v1/evidences/" + report.getEvidenceId() + "/reports/pdf";
    }

    private String analysisRiskLabel(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "적합";
            case MEDIUM -> "주의";
            case HIGH -> "위험";
        };
    }

    private String compareVerdictLabel(CompareVerdict verdict) {
        return switch (verdict) {
            case ORIGINAL_MATCH -> "원본 일치";
            case TAMPERED -> "위변조 의심";
            case INCONCLUSIVE -> "판정 불가";
        };
    }
}
