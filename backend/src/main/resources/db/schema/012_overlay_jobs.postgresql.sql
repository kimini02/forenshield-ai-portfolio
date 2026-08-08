-- overlay_jobs: on-demand module overlay generation
-- prod ddl-auto=validate — 배포 전 RDS에 1회 실행

CREATE TABLE IF NOT EXISTS overlay_jobs (
    overlay_job_id       BIGSERIAL PRIMARY KEY,
    evidence_id          BIGINT       NOT NULL REFERENCES evidences (evidence_id) ON DELETE CASCADE,
    analysis_request_id  BIGINT       NOT NULL,
    module               VARCHAR(40)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    progress_percent     INTEGER      NOT NULL DEFAULT 0,
    overlay_video_url    VARCHAR(2000),
    error_code           VARCHAR(50),
    error_message        TEXT,
    requested_by         BIGINT       NOT NULL,
    requested_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,

    CONSTRAINT chk_overlay_jobs_status
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_overlay_jobs_evidence_module_status
    ON overlay_jobs (evidence_id, module, status);

CREATE INDEX IF NOT EXISTS idx_overlay_jobs_requested_at
    ON overlay_jobs (requested_at DESC);
