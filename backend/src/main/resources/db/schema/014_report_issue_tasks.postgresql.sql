-- Durable report issuance tasks created atomically with case approval.
CREATE TABLE IF NOT EXISTS report_issue_tasks (
    report_issue_task_id BIGSERIAL PRIMARY KEY,
    case_profile_id      BIGINT      NOT NULL REFERENCES case_profiles (case_profile_id),
    evidence_id          BIGINT      NOT NULL REFERENCES evidences (evidence_id),
    analysis_result_id   BIGINT      NOT NULL REFERENCES analysis_results (analysis_result_id),
    requested_by         BIGINT      NOT NULL REFERENCES users (user_id),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count        INTEGER     NOT NULL DEFAULT 0,
    last_error           TEXT,
    next_retry_at        TIMESTAMP,
    artifact_path        VARCHAR(2000),
    created_at           TIMESTAMP   NOT NULL,
    started_at           TIMESTAMP,
    completed_at         TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL,
    CONSTRAINT chk_report_issue_tasks_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_report_issue_tasks_polling
    ON report_issue_tasks (status, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_report_issue_tasks_case_profile
    ON report_issue_tasks (case_profile_id);

CREATE INDEX IF NOT EXISTS idx_report_issue_tasks_analysis_result
    ON report_issue_tasks (analysis_result_id);
