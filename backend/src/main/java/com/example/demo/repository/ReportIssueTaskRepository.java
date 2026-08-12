package com.example.demo.repository;

import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportIssueTaskRepository extends JpaRepository<ReportIssueTask, Long> {

    @Query("SELECT task.status FROM ReportIssueTask task WHERE task.caseProfileId = :caseProfileId")
    List<ReportIssueTaskStatus> findStatusesByCaseProfileId(@Param("caseProfileId") Long caseProfileId);

    @Query(value = """
            SELECT *
            FROM report_issue_tasks
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
            ORDER BY created_at, report_issue_task_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReportIssueTask> findClaimableForUpdate(@Param("batchSize") int batchSize);

    @Query(value = """
            SELECT *
            FROM report_issue_tasks
            WHERE status = 'PROCESSING'
              AND started_at < :cutoff
            ORDER BY started_at, report_issue_task_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReportIssueTask> findStaleProcessingForUpdate(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize
    );
}
