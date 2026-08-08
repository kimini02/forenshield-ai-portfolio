-- =============================================================================
-- ForenShield AI — ERD v3 PostgreSQL DDL
-- Reference: backend/docs/database/erd.md
--
-- EC2 실행 예시:
--   psql -h localhost -U forenshield -d forenshield -f 001_forenshield_erd_v3.postgresql.sql
--
-- 주의: 신규 DB 초기 구축용입니다. 기존 evidence(단수) 테이블이 있으면
--       하단 마이그레이션 섹션을 참고하세요.
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id             BIGSERIAL PRIMARY KEY,
    login_id            VARCHAR(100)  NOT NULL,
    email               VARCHAR(255)  NOT NULL,
    password            VARCHAR(255)  NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    phone               VARCHAR(30),
    organization_type   VARCHAR(30)   NOT NULL,
    department          VARCHAR(255)  NOT NULL,
    position            VARCHAR(255),
    role                VARCHAR(20)   NOT NULL DEFAULT 'ROLE_USER',
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    dark_mode           BOOLEAN       NOT NULL DEFAULT FALSE,
    invite_code_id      BIGINT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT uq_users_login_id UNIQUE (login_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (
        role IN ('ROLE_USER', 'ROLE_ADMIN', 'ROLE_INVESTIGATOR', 'ROLE_REVIEWER', 'ROLE_ORG_ADMIN')
    ),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT chk_users_org_type CHECK (
        organization_type IN ('POLICE', 'PROSECUTION', 'NFS', 'PUBLIC_SECURITY', 'ETC')
    )
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

-- ---------------------------------------------------------------------------
-- 2. invite_codes
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invite_codes (
    invite_code_id      BIGSERIAL PRIMARY KEY,
    code                VARCHAR(100)  NOT NULL,
    organization_type   VARCHAR(30)   NOT NULL,
    issued_by           BIGINT        NOT NULL REFERENCES users (user_id),
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    used_at             TIMESTAMPTZ,

    CONSTRAINT uq_invite_codes_code UNIQUE (code),
    CONSTRAINT chk_invite_codes_status CHECK (
        status IN ('ACTIVE', 'USED', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT chk_invite_codes_org_type CHECK (
        organization_type IN ('POLICE', 'PROSECUTION', 'NFS', 'PUBLIC_SECURITY', 'ETC')
    )
);

CREATE INDEX IF NOT EXISTS idx_invite_codes_status ON invite_codes (status);
CREATE INDEX IF NOT EXISTS idx_invite_codes_issued_by ON invite_codes (issued_by);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS fk_users_invite_code;

ALTER TABLE users
    ADD CONSTRAINT fk_users_invite_code
    FOREIGN KEY (invite_code_id) REFERENCES invite_codes (invite_code_id);

-- ---------------------------------------------------------------------------
-- 3. evidences
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS evidences (
    evidence_id             BIGSERIAL PRIMARY KEY,
    uploader_id             BIGINT        NOT NULL REFERENCES users (user_id),
    case_number             VARCHAR(100),
    case_name               VARCHAR(255),
    file_name               VARCHAR(500)  NOT NULL,
    file_type               VARCHAR(20)   NOT NULL,
    mime_type               VARCHAR(100)  NOT NULL,
    file_size               BIGINT        NOT NULL,
    hash_algorithm          VARCHAR(20)   NOT NULL DEFAULT 'SHA-256',
    original_hash_value     VARCHAR(64)   NOT NULL,
    original_storage_path   TEXT          NOT NULL,
    copy_hash_value         VARCHAR(64),
    copy_storage_path       TEXT,
    copy_status             VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    copy_created_at         TIMESTAMPTZ,
    copy_deleted_at         TIMESTAMPTZ,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'UPLOADED',
    uploaded_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT chk_evidences_file_type CHECK (file_type IN ('VIDEO')),
    CONSTRAINT chk_evidences_copy_status CHECK (copy_status IN ('NONE', 'ACTIVE', 'DELETED')),
    CONSTRAINT chk_evidences_status CHECK (status IN ('UPLOADED', 'DELETED')),
    CONSTRAINT chk_evidences_status_deleted_at CHECK (
        (status = 'UPLOADED' AND deleted_at IS NULL)
        OR (status = 'DELETED' AND deleted_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_evidences_uploader_id ON evidences (uploader_id);
CREATE INDEX IF NOT EXISTS idx_evidences_case_number ON evidences (case_number);
CREATE INDEX IF NOT EXISTS idx_evidences_case_name ON evidences (case_name);
CREATE INDEX IF NOT EXISTS idx_evidences_original_hash ON evidences (original_hash_value);
CREATE INDEX IF NOT EXISTS idx_evidences_status ON evidences (status);

-- ---------------------------------------------------------------------------
-- 4. evidence_metadata
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS evidence_metadata (
    metadata_id         BIGSERIAL PRIMARY KEY,
    evidence_id         BIGINT        NOT NULL UNIQUE REFERENCES evidences (evidence_id) ON DELETE CASCADE,
    width               INTEGER,
    height              INTEGER,
    duration_sec        INTEGER,
    fps                 DOUBLE PRECISION,
    codec               VARCHAR(100),
    sample_rate         INTEGER,
    channels            INTEGER,
    captured_at         TIMESTAMPTZ,
    device_info         VARCHAR(500),
    exif_json           JSONB,
    ffprobe_json        JSONB,
    extraction_status   VARCHAR(20)   NOT NULL DEFAULT 'FAILED',
    extraction_error    TEXT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_evidence_metadata_extraction_status CHECK (
        extraction_status IN ('SUCCESS', 'PARTIAL', 'FAILED')
    )
);

-- ---------------------------------------------------------------------------
-- 5. analysis_requests
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analysis_requests (
    analysis_request_id BIGSERIAL PRIMARY KEY,
    evidence_id         BIGINT        NOT NULL REFERENCES evidences (evidence_id),
    requested_by        BIGINT        NOT NULL REFERENCES users (user_id),
    status              VARCHAR(20)   NOT NULL DEFAULT 'QUEUED',
    requested_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    error_code          VARCHAR(50),
    error_message       TEXT,

    CONSTRAINT chk_analysis_requests_status CHECK (
        status IN ('QUEUED', 'ANALYZING', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_analysis_requests_evidence_id ON analysis_requests (evidence_id);
CREATE INDEX IF NOT EXISTS idx_analysis_requests_requested_by ON analysis_requests (requested_by);
CREATE INDEX IF NOT EXISTS idx_analysis_requests_status ON analysis_requests (status);

-- 증거당 COMPLETED 분석 1건만 허용 (재분석 불허)
CREATE UNIQUE INDEX IF NOT EXISTS uq_analysis_requests_one_completed
    ON analysis_requests (evidence_id)
    WHERE status = 'COMPLETED';

-- ---------------------------------------------------------------------------
-- 6. analysis_results
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analysis_results (
    analysis_result_id  BIGSERIAL PRIMARY KEY,
    analysis_request_id BIGINT        NOT NULL UNIQUE REFERENCES analysis_requests (analysis_request_id) ON DELETE CASCADE,
    risk_score          DOUBLE PRECISION,
    confidence_score    DOUBLE PRECISION,
    risk_level          VARCHAR(20),
    summary             TEXT,
    analyzed_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_analysis_results_risk_level CHECK (
        risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH')
    )
);

-- ---------------------------------------------------------------------------
-- 7. analysis_module_results
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analysis_module_results (
    module_result_id    BIGSERIAL PRIMARY KEY,
    analysis_result_id  BIGINT        NOT NULL REFERENCES analysis_results (analysis_result_id) ON DELETE CASCADE,
    file_type           VARCHAR(20),
    module_name         VARCHAR(100)  NOT NULL,
    detected            BOOLEAN,
    score               DOUBLE PRECISION,
    confidence          DOUBLE PRECISION,
    model_name          VARCHAR(100),
    model_version       VARCHAR(50),
    evidence_text       TEXT,
    details_json        JSONB,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_analysis_module_results_file_type CHECK (
        file_type IS NULL OR file_type IN ('VIDEO')
    )
);

CREATE INDEX IF NOT EXISTS idx_analysis_module_results_analysis_result_id
    ON analysis_module_results (analysis_result_id);

-- ---------------------------------------------------------------------------
-- 8. reports
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    report_id           BIGSERIAL PRIMARY KEY,
    analysis_result_id  BIGINT        REFERENCES analysis_results (analysis_result_id),
    compare_id          BIGINT,
    evidence_id         BIGINT        NOT NULL REFERENCES evidences (evidence_id),
    created_by          BIGINT        NOT NULL REFERENCES users (user_id),
    report_file_name    VARCHAR(500),
    storage_path        TEXT          NOT NULL,
    report_hash         VARCHAR(64),
    file_size           BIGINT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reports_evidence_id ON reports (evidence_id);
CREATE INDEX IF NOT EXISTS idx_reports_analysis_result_id ON reports (analysis_result_id);
CREATE INDEX IF NOT EXISTS idx_reports_compare_id ON reports (compare_id);
CREATE INDEX IF NOT EXISTS idx_reports_created_by ON reports (created_by);

-- ---------------------------------------------------------------------------
-- 8b. blockchain_anchors
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS blockchain_anchors (
    anchor_id           BIGSERIAL PRIMARY KEY,
    anchor_type         VARCHAR(30)   NOT NULL,
    subject_hash        VARCHAR(64)   NOT NULL,
    evidence_id         BIGINT        REFERENCES evidences (evidence_id),
    report_id           BIGINT        REFERENCES reports (report_id),
    created_by          BIGINT        REFERENCES users (user_id),
    merkle_batch_date   DATE,
    merkle_leaf_count   INTEGER,
    status              VARCHAR(20)   NOT NULL,
    transaction_hash    VARCHAR(128),
    block_number        BIGINT,
    network             VARCHAR(50),
    anchored_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    error_message       TEXT,

    CONSTRAINT chk_blockchain_anchors_type CHECK (
        anchor_type IN ('EVIDENCE_HASH', 'REPORT_HASH', 'MERKLE_ROOT')
    ),
    CONSTRAINT chk_blockchain_anchors_status CHECK (
        status IN ('PENDING', 'ANCHORED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_evidence_id ON blockchain_anchors (evidence_id);
CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_report_id ON blockchain_anchors (report_id);
CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_merkle_batch ON blockchain_anchors (merkle_batch_date, anchor_type);
CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_created_at ON blockchain_anchors (created_at);

-- ---------------------------------------------------------------------------
-- 9. custody_logs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS custody_logs (
    log_id              BIGSERIAL PRIMARY KEY,
    actor_id            BIGINT        NOT NULL REFERENCES users (user_id),
    target_type         VARCHAR(30)   NOT NULL,
    target_id           BIGINT        NOT NULL,
    action_type         VARCHAR(50)   NOT NULL,
    subject_hash        VARCHAR(64),
    storage_path_at_event TEXT,
    reason              TEXT,
    client_ip           VARCHAR(45),
    event_payload_json  JSONB,
    previous_log_hash   VARCHAR(64),
    current_log_hash    VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_custody_logs_target_type CHECK (
        target_type IN (
            'USER', 'EVIDENCE', 'ANALYSIS_REQUEST',
            'ANALYSIS_RESULT', 'REPORT', 'SYSTEM'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_custody_logs_actor_id ON custody_logs (actor_id);
CREATE INDEX IF NOT EXISTS idx_custody_logs_target ON custody_logs (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_custody_logs_action_type ON custody_logs (action_type);
CREATE INDEX IF NOT EXISTS idx_custody_logs_created_at ON custody_logs (created_at);

COMMIT;

-- =============================================================================
-- (선택) 레거시 evidence(단수) 테이블 → evidences 마이그레이션
-- 기존 데이터가 있을 때만 수동 실행하세요.
-- =============================================================================
--
-- INSERT INTO evidences (
--     evidence_id, uploader_id, file_name, file_type, mime_type, file_size,
--     hash_algorithm, original_hash_value, original_storage_path,
--     status, uploaded_at
-- )
-- SELECT
--     e.evidence_id,
--     1,  -- TODO: 실제 uploader user_id
--     e.file_name,
--     'VIDEO',  -- TODO: mime 기반 매핑
--     'application/octet-stream',
--     0,
--     COALESCE(e.hash_algorithm, 'SHA-256'),
--     e.hash_value,
--     e.original_storage_path,
--     'UPLOADED',
--     e.uploaded_at
-- FROM evidence e;
--
-- SELECT setval(
--     pg_get_serial_sequence('evidences', 'evidence_id'),
--     COALESCE((SELECT MAX(evidence_id) FROM evidences), 1)
-- );
