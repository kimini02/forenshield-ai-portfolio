-- 기존 RDS 마이그레이션: reports compare PDF + blockchain_anchors
-- prod (ddl-auto=validate) 배포 전 1회 실행

BEGIN;

ALTER TABLE reports
    ALTER COLUMN analysis_result_id DROP NOT NULL;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS compare_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_reports_compare_id ON reports (compare_id);

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

COMMIT;
