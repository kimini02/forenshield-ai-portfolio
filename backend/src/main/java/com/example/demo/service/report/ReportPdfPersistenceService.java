package com.example.demo.service.report;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.custody.ReportCustodyLogService;
import com.example.demo.service.report.ReportPdfService.ReportPdfPayload;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PDF persistence runs in a dedicated write transaction so read-only access checks
 * ({@code EvidenceAccessService}) do not mark the connection read-only.
 */
@Service
@RequiredArgsConstructor
public class ReportPdfPersistenceService {

    private final ReportRepository reportRepository;
    private final ReportContentBuilder reportContentBuilder;
    private final ReportPdfStorageService reportPdfStorageService;
    private final ReportCustodyLogService reportCustodyLogService;

    @Transactional
    public ReportPdfPayload persistEvidenceReport(
            User user,
            Long evidenceId,
            boolean preview,
            Evidence evidence,
            AnalysisRequest request,
            AnalysisResult result,
            List<AnalysisModuleResult> modules,
            boolean approvedForReport
    ) {
        List<String> lines = reportContentBuilder.buildEvidenceLines(evidence, request, result, modules);
        if (!preview && !approvedForReport) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_NOT_APPROVED",
                    "검토 승인 완료 후 최종 PDF를 다운로드할 수 있습니다."
            );
        }

        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(result.getAnalysisResultId())
                .orElseGet(() -> reportPdfStorageService.persistAnalysisReport(
                        result.getAnalysisResultId(),
                        evidenceId,
                        evidence.getUploaderId(),
                        "analysis-report-" + evidenceId + ".pdf",
                        lines,
                        "ForenShield Analysis Report"
                ));

        if (approvedForReport && !report.isIssued()) {
            report = reportPdfStorageService.issueReport(
                    report,
                    user.getUserId(),
                    lines,
                    "ForenShield Analysis Report"
            );
        }

        if (!preview && !report.isIssued()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_NOT_APPROVED",
                    "검토 승인 완료 후 최종 PDF를 다운로드할 수 있습니다."
            );
        }

        byte[] pdfBytes = reportPdfStorageService.ensureReportPdfBytes(
                report,
                lines,
                "ForenShield Analysis Report"
        );
        if (preview) {
            return new ReportPdfPayload(
                    report.getReportFileName(),
                    reportPdfStorageService.addPreviewWatermark(pdfBytes),
                    report.getReportHash(),
                    report.getPublicationStatus().name(),
                    report.getReportVersion()
            );
        }

        reportCustodyLogService.recordReportDownloaded(user.getUserId(), report);
        return toPayload(report, pdfBytes);
    }

    @Transactional
    public ReportPdfPayload persistCompareReport(
            User user,
            Long compareId,
            boolean preview,
            CompareVerification verification,
            Evidence original,
            CompareFileInfoDto originalInfo,
            CompareFileInfoDto candidateInfo,
            List<CompareItemDto> items,
            boolean approvedForReport
    ) {
        List<String> lines = reportContentBuilder.buildCompareLines(
                verification, original, originalInfo, candidateInfo, items);

        if (!preview && !approvedForReport) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_NOT_APPROVED",
                    "검토 승인 완료 후 비교검증 PDF를 다운로드할 수 있습니다."
            );
        }

        Report report = reportRepository.findTopByCompareIdOrderByCreatedAtDesc(compareId)
                .orElseGet(() -> reportPdfStorageService.persistCompareReport(
                        compareId,
                        original.getEvidenceId(),
                        user.getUserId(),
                        "compare-report-" + compareId + ".pdf",
                        lines,
                        "ForenShield Compare Verification Report"
                ));

        if (approvedForReport && !report.isIssued()) {
            report = reportPdfStorageService.issueReport(
                    report,
                    user.getUserId(),
                    lines,
                    "ForenShield Compare Verification Report"
            );
        }

        if (!preview && !report.isIssued()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_NOT_APPROVED",
                    "검토 승인 완료 후 비교검증 PDF를 다운로드할 수 있습니다."
            );
        }

        byte[] pdfBytes = reportPdfStorageService.ensureReportPdfBytes(
                report,
                lines,
                "ForenShield Compare Verification Report"
        );
        if (preview) {
            return new ReportPdfPayload(
                    report.getReportFileName(),
                    reportPdfStorageService.addPreviewWatermark(pdfBytes),
                    report.getReportHash(),
                    report.getPublicationStatus().name(),
                    report.getReportVersion()
            );
        }

        reportCustodyLogService.recordReportDownloaded(user.getUserId(), report);
        return toPayload(report, pdfBytes);
    }

    private ReportPdfPayload toPayload(Report report, byte[] pdfBytes) {
        return new ReportPdfPayload(
                report.getReportFileName(),
                pdfBytes,
                report.getReportHash(),
                report.getPublicationStatus().name(),
                report.getReportVersion()
        );
    }
}
