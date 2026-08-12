package com.example.demo.service.report;

import com.example.demo.config.ReportIssueWorkerProperties;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Report;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportRepository;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportIssueTaskRecoveryService {

    private final ReportIssueWorkerProperties properties;
    private final ReportIssueTaskRepository reportIssueTaskRepository;
    private final ReportRepository reportRepository;
    private final BlockchainAnchorRepository blockchainAnchorRepository;

    @Transactional
    public int recoverStaleTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(Duration.ofMillis(properties.getProcessingTimeoutMs()));
        List<ReportIssueTask> tasks = reportIssueTaskRepository
                .findStaleProcessingForUpdate(cutoff, properties.getBatchSize());
        for (ReportIssueTask task : tasks) {
            recover(task, now);
        }
        return tasks.size();
    }

    private void recover(ReportIssueTask task, LocalDateTime now) {
        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(task.getAnalysisResultId())
                .filter(Report::isIssued)
                .orElse(null);
        if (report == null) {
            task.recover(now, "stale PROCESSING task recovered before Report issuance", now);
            return;
        }

        BlockchainAnchor anchor = blockchainAnchorRepository
                .findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                        report.getReportId(), BlockchainAnchorType.REPORT_HASH)
                .orElse(null);
        if (anchor == null) {
            task.recover(now, "stale PROCESSING task recovered before anchor preparation", now);
            return;
        }
        if (anchor.getStatus() == BlockchainAnchorStatus.PENDING) {
            anchor.setStatus(BlockchainAnchorStatus.FAILED);
            anchor.setErrorCode("ANCHOR_OUTCOME_UNKNOWN");
            anchor.setErrorMessage("Manual reconciliation required: stale PENDING anchor after worker interruption");
        }
        task.complete(now);
    }
}
