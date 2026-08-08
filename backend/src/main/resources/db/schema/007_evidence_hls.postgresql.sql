-- evidence_hls: 증거별 HLS 패키징·암호화 재생 메타 (1:1, evidences와 분리)
-- 적용: psql -h $POSTGRES_HOST -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1 -f 007_evidence_hls.postgresql.sql
-- 운영: prod ddl-auto=validate — 배포 전 RDS에 1회 실행 (kubectl Job / VPN psql)
-- 로컬 H2 파일 DB: ddl-auto=update 가 엔티티 기준으로 동일 테이블 생성 가능. 본 SQL은 RDS·스테이징용.

CREATE TABLE IF NOT EXISTS evidence_hls (
    evidence_id         BIGINT PRIMARY KEY
                        REFERENCES evidences (evidence_id) ON DELETE CASCADE,
    hls_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    hls_storage_prefix  VARCHAR(500),
    hls_packaged_at     TIMESTAMPTZ,
    content_key_enc     BYTEA,
    hls_error           TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_evidence_hls_status
        CHECK (hls_status IN ('PENDING', 'PACKAGING', 'READY', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_evidence_hls_status
    ON evidence_hls (hls_status);

CREATE INDEX IF NOT EXISTS idx_evidence_hls_packaging_updated
    ON evidence_hls (updated_at)
    WHERE hls_status = 'PACKAGING';

COMMENT ON TABLE evidence_hls IS
    'HLS AES-128 패키징 상태·S3 prefix·암호화 키. 재생 전용 — 원본 mp4는 evidences.original_storage_path';
COMMENT ON COLUMN evidence_hls.content_key_enc IS
    'AES-128 content key (앱 레벨 암호화 바이트). S3·m3u8에 평문 저장 금지';
