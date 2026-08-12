package com.example.demo.domain;

import com.example.demo.domain.enums.ReportIssueTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "report_issue_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_report_issue_tasks_analysis_result_id",
                columnNames = "analysis_result_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportIssueTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_issue_task_id")
    private Long reportIssueTaskId;

    @Column(name = "case_profile_id", nullable = false)
    private Long caseProfileId;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "analysis_result_id", nullable = false)
    private Long analysisResultId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportIssueTaskStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "artifact_path", length = 2000)
    private String artifactPath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ReportIssueTask pending(
            Long caseProfileId,
            Long evidenceId,
            Long analysisResultId,
            Long requestedBy
    ) {
        LocalDateTime now = LocalDateTime.now();
        ReportIssueTask task = new ReportIssueTask();
        task.caseProfileId = caseProfileId;
        task.evidenceId = evidenceId;
        task.analysisResultId = analysisResultId;
        task.requestedBy = requestedBy;
        task.status = ReportIssueTaskStatus.PENDING;
        task.attemptCount = 0;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public void claim(LocalDateTime startedAt) {
        if (status != ReportIssueTaskStatus.PENDING) {
            throw new IllegalStateException("Only PENDING report issue tasks can be claimed");
        }
        this.status = ReportIssueTaskStatus.PROCESSING;
        this.startedAt = startedAt;
        this.attemptCount++;
        this.nextRetryAt = null;
        this.lastError = null;
        this.updatedAt = startedAt;
    }

    public void recordArtifact(String artifactPath, LocalDateTime updatedAt) {
        this.artifactPath = artifactPath;
        this.updatedAt = updatedAt;
    }

    public void scheduleRetry(String error, LocalDateTime nextRetryAt, LocalDateTime updatedAt) {
        this.status = ReportIssueTaskStatus.PENDING;
        this.lastError = error;
        this.nextRetryAt = nextRetryAt;
        this.startedAt = null;
        this.updatedAt = updatedAt;
    }

    public void fail(String error, LocalDateTime failedAt) {
        this.status = ReportIssueTaskStatus.FAILED;
        this.lastError = error;
        this.nextRetryAt = null;
        this.completedAt = failedAt;
        this.updatedAt = failedAt;
    }

    public void complete(LocalDateTime completedAt) {
        this.status = ReportIssueTaskStatus.COMPLETED;
        this.lastError = null;
        this.nextRetryAt = null;
        this.completedAt = completedAt;
        this.updatedAt = completedAt;
    }

    public void recover(LocalDateTime nextRetryAt, String reason, LocalDateTime updatedAt) {
        this.status = ReportIssueTaskStatus.PENDING;
        this.startedAt = null;
        this.nextRetryAt = nextRetryAt;
        this.lastError = reason;
        this.updatedAt = updatedAt;
    }
}
