package com.example.demo.service.report;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.enums.ReportPublicationStatus;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.custody.ReportCustodyLogService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportIssuePersistenceService {

    private final ReportIssueTaskRepository reportIssueTaskRepository;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final ReportRepository reportRepository;
    private final ReportContentBuilder reportContentBuilder;
    private final ReportPublicationSnapshotService reportPublicationSnapshotService;
    private final ReportCustodyLogService reportCustodyLogService;

    @Transactional(readOnly = true)
    public WorkContext loadWorkContext(Long taskId) {
        ReportIssueTask task = reportIssueTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Report issue task not found: " + taskId));
        Evidence evidence = evidenceRepository.findById(task.getEvidenceId())
                .orElseThrow(() -> new IllegalStateException("Evidence not found for report issue task: " + taskId));
        AnalysisResult result = analysisResultRepository.findById(task.getAnalysisResultId())
                .orElseThrow(() -> new IllegalStateException("Analysis result not found for report issue task: " + taskId));
        AnalysisRequest request = analysisRequestRepository.findById(result.getAnalysisRequestId())
                .orElseThrow(() -> new IllegalStateException("Analysis request not found for report issue task: " + taskId));
        List<AnalysisModuleResult> modules = analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());
        List<String> lines = List.copyOf(reportContentBuilder.buildEvidenceLines(evidence, request, result, modules));
        Report existing = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(result.getAnalysisResultId())
                .orElse(null);
        return new WorkContext(
                taskId,
                task.getAnalysisResultId(),
                task.getEvidenceId(),
                evidence.getUploaderId(),
                task.getRequestedBy(),
                existing == null ? null : existing.getReportId(),
                existing != null && existing.isIssued(),
                lines
        );
    }

    @Transactional(readOnly = true)
    public Report findIssuedReport(Long analysisResultId) {
        return reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(analysisResultId)
                .filter(Report::isIssued)
                .orElse(null);
    }

    @Transactional
    public Report persistIssuedReport(
            WorkContext context,
            ReportPdfStorageService.IssuedReportArtifact artifact
    ) {
        Report latest = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(context.analysisResultId())
                .orElse(null);
        if (latest != null && latest.isIssued()) {
            return latest;
        }

        Report report = latest == null ? new Report() : latest;
        if (latest == null) {
            int version = Math.toIntExact(
                    reportRepository.countByEvidenceIdAndCompareIdIsNull(context.evidenceId()) + 1);
            report.setAnalysisResultId(context.analysisResultId());
            report.setEvidenceId(context.evidenceId());
            report.setCreatedBy(context.reportCreatedBy());
            report.setReportVersion(version);
            report.setCreatedAt(LocalDateTime.now());
        }
        report.setReportFileName(artifact.fileName());
        report.setStoragePath(artifact.storagePath());
        report.setReportHash(artifact.reportHash());
        report.setFileSize(artifact.fileSize());
        report.setReportNo(artifact.reportNo());
        report.setVerificationToken(artifact.verificationToken());
        report.setVerificationCode(artifact.verificationCode());
        report.setPublicationStatus(ReportPublicationStatus.DRAFT);
        report.markIssued(context.requestedBy(), LocalDateTime.now());

        Report saved = reportRepository.saveAndFlush(report);
        reportPublicationSnapshotService.createIfAbsent(saved, context.lines());
        supersedeOlderReports(saved);
        try {
            reportCustodyLogService.recordReportCreated(context.requestedBy(), saved);
        } catch (Exception ex) {
            log.warn("Report custody log failed reportId={} evidenceId={}: {}",
                    saved.getReportId(), saved.getEvidenceId(), ex.getMessage());
        }
        return saved;
    }

    private void supersedeOlderReports(Report issuedReport) {
        LocalDateTime now = LocalDateTime.now();
        List<Report> older = reportRepository
                .findByEvidenceIdAndCompareIdIsNullOrderByCreatedAtDesc(issuedReport.getEvidenceId())
                .stream()
                .filter(candidate -> !candidate.getReportId().equals(issuedReport.getReportId()))
                .filter(Report::isIssued)
                .toList();
        older.forEach(report -> report.markSuperseded(now));
        reportRepository.saveAll(older);
    }

    public record WorkContext(
            Long taskId,
            Long analysisResultId,
            Long evidenceId,
            Long reportCreatedBy,
            Long requestedBy,
            Long existingReportId,
            boolean reportIssued,
            List<String> lines
    ) {
    }
}
