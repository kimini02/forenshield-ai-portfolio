package com.example.demo.service.report;

import com.example.demo.config.ReportIssueWorkerProperties;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Report;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportIssueTaskRecoveryServiceTest {

    @Mock
    private ReportIssueTaskRepository taskRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private BlockchainAnchorRepository anchorRepository;

    private ReportIssueTaskRecoveryService service;

    @BeforeEach
    void setUp() {
        ReportIssueWorkerProperties properties = new ReportIssueWorkerProperties();
        properties.setBatchSize(10);
        properties.setProcessingTimeoutMs(1_000);
        service = new ReportIssueTaskRecoveryService(
                properties, taskRepository, reportRepository, anchorRepository);
    }

    @Test
    void recoverStaleTasks_withoutIssuedReportReturnsTaskToPending() {
        ReportIssueTask task = processingTask();
        when(taskRepository.findStaleProcessingForUpdate(any(), eq(10))).thenReturn(List.of(task));
        when(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.empty());

        assertThat(service.recoverStaleTasks()).isEqualTo(1);

        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.PENDING);
        assertThat(task.getLastError()).contains("before Report issuance");
    }

    @Test
    void recoverStaleTasks_withTerminalAnchorCompletesTask() {
        ReportIssueTask task = processingTask();
        Report report = issuedReport(50L);
        BlockchainAnchor anchor = anchor(BlockchainAnchorStatus.ANCHORED);
        when(taskRepository.findStaleProcessingForUpdate(any(), eq(10))).thenReturn(List.of(task));
        when(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(report));
        when(anchorRepository.findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                50L, BlockchainAnchorType.REPORT_HASH)).thenReturn(Optional.of(anchor));

        service.recoverStaleTasks();

        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
    }

    @Test
    void recoverStaleTasks_withPendingAnchorMarksOutcomeUnknownWithoutHttpRetry() {
        ReportIssueTask task = processingTask();
        Report report = issuedReport(50L);
        BlockchainAnchor anchor = anchor(BlockchainAnchorStatus.PENDING);
        when(taskRepository.findStaleProcessingForUpdate(any(), eq(10))).thenReturn(List.of(task));
        when(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(report));
        when(anchorRepository.findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                50L, BlockchainAnchorType.REPORT_HASH)).thenReturn(Optional.of(anchor));

        service.recoverStaleTasks();

        assertThat(anchor.getStatus()).isEqualTo(BlockchainAnchorStatus.FAILED);
        assertThat(anchor.getErrorCode()).isEqualTo("ANCHOR_OUTCOME_UNKNOWN");
        assertThat(anchor.getErrorMessage()).contains("Manual reconciliation required");
        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
    }

    private ReportIssueTask processingTask() {
        ReportIssueTask task = ReportIssueTask.pending(1L, 2L, 3L, 4L);
        task.claim(LocalDateTime.now().minusMinutes(10));
        return task;
    }

    private Report issuedReport(Long reportId) {
        Report report = mock(Report.class);
        when(report.isIssued()).thenReturn(true);
        when(report.getReportId()).thenReturn(reportId);
        return report;
    }

    private BlockchainAnchor anchor(BlockchainAnchorStatus status) {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(status);
        return anchor;
    }
}
