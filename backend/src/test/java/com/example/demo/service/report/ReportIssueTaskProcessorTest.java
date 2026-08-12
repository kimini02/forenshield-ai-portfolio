package com.example.demo.service.report;

import com.example.demo.domain.Report;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.blockchain.BlockchainAnchorService.PreparedReportAnchor;
import com.example.demo.service.blockchain.client.BlockchainAnchorRequest;
import com.example.demo.service.blockchain.client.BlockchainAnchorResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportIssueTaskProcessorTest {

    @Mock
    private ReportIssueTaskStateService taskStateService;
    @Mock
    private ReportIssuePersistenceService persistenceService;
    @Mock
    private ReportPdfStorageService reportPdfStorageService;
    @Mock
    private BlockchainAnchorService blockchainAnchorService;

    private ReportIssueTaskProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ReportIssueTaskProcessor(
                taskStateService,
                persistenceService,
                reportPdfStorageService,
                blockchainAnchorService
        );
    }

    @Test
    void process_issuesReportAnchorsAndCompletesTask() {
        var context = context();
        var artifact = artifact();
        Report report = report(50L);
        PreparedReportAnchor prepared = prepared(70L, true, BlockchainAnchorStatus.PENDING);
        BlockchainAnchorResult result = new BlockchainAnchorResult("tx-1", 1L, true, null);
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(null);
        when(reportPdfStorageService.renderAndStoreIssuedAnalysisReport(1L, 20L, context.lines(),
                "ForenShield Analysis Report")).thenReturn(artifact);
        when(persistenceService.persistIssuedReport(context, artifact)).thenReturn(report);
        when(blockchainAnchorService.prepareReportAnchor(report, 40L)).thenReturn(prepared);
        when(blockchainAnchorService.submitPreparedReportAnchor(prepared)).thenReturn(result);

        processor.process(1L);

        verify(taskStateService).recordArtifact(1L, artifact.storagePath());
        verify(blockchainAnchorService).recordPreparedReportAnchorResult(70L, result);
        verify(taskStateService).complete(1L);
    }

    @Test
    void process_explicitBlockchainFailureStillCompletesTask() {
        var context = context();
        Report report = report(50L);
        PreparedReportAnchor prepared = prepared(70L, true, BlockchainAnchorStatus.PENDING);
        BlockchainAnchorResult result = new BlockchainAnchorResult(null, null, false, "gateway rejected");
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(report);
        when(blockchainAnchorService.prepareReportAnchor(report, 40L)).thenReturn(prepared);
        when(blockchainAnchorService.submitPreparedReportAnchor(prepared)).thenReturn(result);

        processor.process(1L);

        verify(blockchainAnchorService).recordPreparedReportAnchorResult(70L, result);
        verify(taskStateService).complete(1L);
    }

    @Test
    void process_pdfFailureSchedulesTaskFailureHandling() {
        var context = context();
        RuntimeException failure = new RuntimeException("pdf render failed");
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(null);
        when(reportPdfStorageService.renderAndStoreIssuedAnalysisReport(any(), any(), any(), any()))
                .thenThrow(failure);

        processor.process(1L);

        verify(taskStateService).retryOrFail(1L, failure);
        verify(blockchainAnchorService, never()).prepareReportAnchor(any(), any());
    }

    @Test
    void process_reportPersistenceFailureRecordsArtifactBeforeRetry() {
        var context = context();
        var artifact = artifact();
        RuntimeException failure = new RuntimeException("report insert failed");
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(null);
        when(reportPdfStorageService.renderAndStoreIssuedAnalysisReport(any(), any(), any(), any()))
                .thenReturn(artifact);
        when(persistenceService.persistIssuedReport(context, artifact)).thenThrow(failure);

        processor.process(1L);

        verify(taskStateService).recordArtifact(1L, artifact.storagePath());
        verify(taskStateService).retryOrFail(1L, failure);
    }

    @Test
    void process_existingIssuedReportSkipsPdfRendering() {
        var context = context();
        Report report = report(50L);
        PreparedReportAnchor prepared = prepared(70L, false, BlockchainAnchorStatus.ANCHORED);
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(report);
        when(blockchainAnchorService.prepareReportAnchor(report, 40L)).thenReturn(prepared);

        processor.process(1L);

        verify(reportPdfStorageService, never()).renderAndStoreIssuedAnalysisReport(any(), any(), any(), any());
        verify(taskStateService).complete(1L);
    }

    @Test
    void process_existingTerminalAnchorDoesNotCallGateway() {
        var context = context();
        Report report = report(50L);
        PreparedReportAnchor failed = prepared(70L, false, BlockchainAnchorStatus.FAILED);
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(report);
        when(blockchainAnchorService.prepareReportAnchor(report, 40L)).thenReturn(failed);

        processor.process(1L);

        verify(blockchainAnchorService, never()).submitPreparedReportAnchor(any());
        verify(taskStateService).complete(1L);
    }

    @Test
    void process_gatewayWaitRunsWithoutDatabaseTransaction() {
        var context = context();
        Report report = report(50L);
        PreparedReportAnchor prepared = prepared(70L, true, BlockchainAnchorStatus.PENDING);
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(report);
        when(blockchainAnchorService.prepareReportAnchor(report, 40L)).thenReturn(prepared);
        when(blockchainAnchorService.submitPreparedReportAnchor(prepared)).thenAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(
                    TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Thread.sleep(50L);
            return new BlockchainAnchorResult("tx", 1L, true, null);
        });

        processor.process(1L);

        verify(taskStateService).complete(1L);
    }

    @Test
    void process_ambiguousGatewayExceptionMarksOutcomeUnknownWithoutRetry() {
        var context = context();
        Report report = report(50L);
        PreparedReportAnchor prepared = prepared(70L, true, BlockchainAnchorStatus.PENDING);
        when(persistenceService.loadWorkContext(1L)).thenReturn(context);
        when(persistenceService.findIssuedReport(30L)).thenReturn(report);
        when(blockchainAnchorService.prepareReportAnchor(report, 40L)).thenReturn(prepared);
        when(blockchainAnchorService.submitPreparedReportAnchor(prepared))
                .thenThrow(new RuntimeException("read timeout"));

        processor.process(1L);

        verify(blockchainAnchorService).markReportAnchorOutcomeUnknown(70L, "read timeout");
        verify(taskStateService, never()).retryOrFail(any(), any());
        verify(taskStateService).complete(1L);
    }

    private ReportIssuePersistenceService.WorkContext context() {
        return new ReportIssuePersistenceService.WorkContext(
                1L, 30L, 20L, 10L, 40L, null, false, List.of("line"));
    }

    private ReportPdfStorageService.IssuedReportArtifact artifact() {
        return new ReportPdfStorageService.IssuedReportArtifact(
                "report.pdf", "reports/report.pdf", "a".repeat(64), 100L,
                "RPT-1", "token", "code");
    }

    private Report report(Long reportId) {
        return org.mockito.Mockito.mock(Report.class, "report-" + reportId);
    }

    private PreparedReportAnchor prepared(Long id, boolean submit, BlockchainAnchorStatus status) {
        BlockchainAnchorRequest request = submit
                ? BlockchainAnchorRequest.of("a".repeat(64),
                com.example.demo.domain.enums.BlockchainAnchorType.REPORT_HASH,
                "test", "client", 20L, 50L, null, null)
                : null;
        return new PreparedReportAnchor(id, request, submit, status, false);
    }
}
