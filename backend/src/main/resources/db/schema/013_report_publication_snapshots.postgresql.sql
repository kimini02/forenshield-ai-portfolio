-- RQ-DTL-084~088: 발행 시점 보고서 입력과 공개 표시값을 불변 스냅샷으로 보존합니다.
-- 운영 환경은 ddl-auto=validate이므로 backend 배포 전에 1회 적용합니다.
CREATE TABLE IF NOT EXISTS report_publication_snapshots (
    snapshot_id             BIGSERIAL PRIMARY KEY,
    report_id               BIGINT NOT NULL REFERENCES reports (report_id),
    schema_version          VARCHAR(20) NOT NULL,
    pdf_template_version    VARCHAR(40) NOT NULL,
    report_input_json       TEXT NOT NULL,
    public_summary_json     TEXT NOT NULL,
    artifact_manifest_json  TEXT NOT NULL,
    display_policy_json     TEXT NOT NULL,
    snapshot_hash           VARCHAR(64) NOT NULL,
    created_at              TIMESTAMP NOT NULL,
    CONSTRAINT ux_report_publication_snapshots_report UNIQUE (report_id),
    CONSTRAINT ck_report_publication_snapshots_hash CHECK (snapshot_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_report_publication_snapshots_created_at
    ON report_publication_snapshots (created_at);
