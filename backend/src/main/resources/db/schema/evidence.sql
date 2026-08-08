-- Evidence 테이블: 업로드된 원본 증거 파일의 SHA-256 해시 및 저장 경로
CREATE TABLE IF NOT EXISTS evidence (
    evidence_id           BIGSERIAL PRIMARY KEY,
    file_name             VARCHAR(255) NOT NULL,
    case_name             VARCHAR(255),
    hash_algorithm        VARCHAR(20)  NOT NULL DEFAULT 'SHA-256',
    hash_value            VARCHAR(64)  NOT NULL,
    original_storage_path TEXT         NOT NULL,
    uploaded_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evidence_hash_value ON evidence (hash_value);
