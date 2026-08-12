package com.example.demo.service.report;

import com.example.demo.config.ReportIssueWorkerProperties;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.repository.ReportIssueTaskRepository;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportIssueTaskStateService {

    private final ReportIssueTaskRepository reportIssueTaskRepository;
    private final ReportIssueWorkerProperties properties;

    @Transactional
    public List<Long> claimBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<ReportIssueTask> tasks = reportIssueTaskRepository.findClaimableForUpdate(properties.getBatchSize());
        tasks.forEach(task -> task.claim(now));
        reportIssueTaskRepository.saveAllAndFlush(tasks);
        return tasks.stream().map(ReportIssueTask::getReportIssueTaskId).toList();
    }

    @Transactional(readOnly = true)
    public ReportIssueTask requireProcessing(Long taskId) {
        ReportIssueTask task = reportIssueTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Report issue task not found: " + taskId));
        if (task.getStatus() != ReportIssueTaskStatus.PROCESSING) {
            throw new IllegalStateException("Report issue task is not PROCESSING: " + taskId);
        }
        return task;
    }

    @Transactional
    public void recordArtifact(Long taskId, String artifactPath) {
        requireTask(taskId).recordArtifact(artifactPath, LocalDateTime.now());
    }

    @Transactional
    public void complete(Long taskId) {
        ReportIssueTask task = requireTask(taskId);
        if (task.getStatus() != ReportIssueTaskStatus.COMPLETED) {
            task.complete(LocalDateTime.now());
        }
    }

    @Transactional
    public void retryOrFail(Long taskId, Throwable failure) {
        ReportIssueTask task = requireTask(taskId);
        String error = summarize(failure, task.getArtifactPath());
        LocalDateTime now = LocalDateTime.now();
        if (task.getAttemptCount() >= properties.getMaxAttempts()) {
            task.fail(error, now);
            return;
        }
        task.scheduleRetry(error, now.plus(Duration.ofMillis(properties.getRetryDelayMs())), now);
    }

    private ReportIssueTask requireTask(Long taskId) {
        return reportIssueTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Report issue task not found: " + taskId));
    }

    private String summarize(Throwable failure, String artifactPath) {
        String message = failure == null || failure.getMessage() == null
                ? "report issuance failed"
                : failure.getMessage();
        String value = artifactPath == null ? message : message + " [artifactPath=" + artifactPath + "]";
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
