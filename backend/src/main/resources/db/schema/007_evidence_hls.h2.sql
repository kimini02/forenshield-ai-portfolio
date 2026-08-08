-- H2 / 로컬·단위 테스트 (PostgreSQL MODE)
-- ddl-auto=update 와 병행 가능. CI 테스트 DB 초기화 시 참고.

CREATE TABLE IF NOT EXISTS evidence_hls (
    evidence_id         BIGINT PRIMARY KEY,
    hls_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    hls_storage_prefix  VARCHAR(500),
    hls_packaged_at     TIMESTAMP,
    content_key_enc     VARBINARY(32),
    hls_error           CLOB,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_evidence_hls_status
        CHECK (hls_status IN ('PENDING', 'PACKAGING', 'READY', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_evidence_hls_status ON evidence_hls (hls_status);
