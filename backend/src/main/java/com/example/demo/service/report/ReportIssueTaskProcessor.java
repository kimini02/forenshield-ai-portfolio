package com.example.demo.service.report;

import com.example.demo.domain.Report;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.blockchain.BlockchainAnchorService.PreparedReportAnchor;
import com.example.demo.service.blockchain.client.BlockchainAnchorResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportIssueTaskProcessor {

    private static final String REPORT_TITLE = "ForenShield Analysis Report";

    private final ReportIssueTaskStateService taskStateService;
    private final ReportIssuePersistenceService persistenceService;
    private final ReportPdfStorageService reportPdfStorageService;
    private final BlockchainAnchorService blockchainAnchorService;

    /** No transaction on the workflow coordinator: each DB transition is delegated to a short TX service. */
    public void process(Long taskId) {
        ReportIssuePersistenceService.WorkContext context;
        Report report;
        try {
            taskStateService.requireProcessing(taskId);
            context = persistenceService.loadWorkContext(taskId);
            report = persistenceService.findIssuedReport(context.analysisResultId());
            if (report == null) {
                ReportPdfStorageService.IssuedReportArtifact artifact = reportPdfStorageService
                        .renderAndStoreIssuedAnalysisReport(
                                taskId,
                                context.evidenceId(),
                                context.lines(),
                                REPORT_TITLE
                        );
                taskStateService.recordArtifact(taskId, artifact.storagePath());
                report = persistenceService.persistIssuedReport(context, artifact);
            }
        } catch (Exception reportFailure) {
            taskStateService.retryOrFail(taskId, reportFailure);
            log.warn("Report issue task failed before blockchain taskId={}: {}", taskId, reportFailure.getMessage());
            return;
        }

        processBlockchain(taskId, report, context.requestedBy());
    }

    private void processBlockchain(Long taskId, Report report, Long requestedBy) {
        PreparedReportAnchor prepared;
        try {
            prepared = blockchainAnchorService.prepareReportAnchor(report, requestedBy);
        } catch (Exception preparationFailure) {
            taskStateService.retryOrFail(taskId, preparationFailure);
            log.warn("Report anchor preparation failed taskId={}: {}", taskId, preparationFailure.getMessage());
            return;
        }

        if (prepared.disabled()) {
            taskStateService.complete(taskId);
            return;
        }
        if (!prepared.shouldSubmit()) {
            finishExistingAnchor(taskId, prepared);
            return;
        }

        try {
            BlockchainAnchorResult result = blockchainAnchorService.submitPreparedReportAnchor(prepared);
            blockchainAnchorService.recordPreparedReportAnchorResult(prepared.anchorId(), result);
        } catch (Exception uncertainOutcome) {
            blockchainAnchorService.markReportAnchorOutcomeUnknown(
                    prepared.anchorId(),
                    safeMessage(uncertainOutcome)
            );
        }
        taskStateService.complete(taskId);
    }

    private void finishExistingAnchor(Long taskId, PreparedReportAnchor prepared) {
        if (prepared.status() == BlockchainAnchorStatus.PENDING) {
            blockchainAnchorService.markReportAnchorOutcomeUnknown(
                    prepared.anchorId(),
                    "stale PENDING anchor encountered during report task resume"
            );
        }
        taskStateService.complete(taskId);
    }

    private String safeMessage(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
