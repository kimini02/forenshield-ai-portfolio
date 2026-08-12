package com.example.demo.scheduler;

import com.example.demo.service.report.ReportIssueTaskProcessor;
import com.example.demo.service.report.ReportIssueTaskRecoveryService;
import com.example.demo.service.report.ReportIssueTaskStateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "report.issue.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ReportIssueTaskScheduler {

    private final ReportIssueTaskStateService taskStateService;
    private final ReportIssueTaskRecoveryService recoveryService;
    private final ReportIssueTaskProcessor processor;

    @Scheduled(fixedDelayString = "${report.issue.worker.poll-interval-ms:5000}")
    public void poll() {
        int recovered = recoveryService.recoverStaleTasks();
        if (recovered > 0) {
            log.warn("Recovered {} stale report issue tasks", recovered);
        }

        List<Long> taskIds = taskStateService.claimBatch();
        for (Long taskId : taskIds) {
            try {
                processor.process(taskId);
            } catch (Exception unexpected) {
                log.error("Unexpected report issue worker failure taskId={}", taskId, unexpected);
            }
        }
    }
}
