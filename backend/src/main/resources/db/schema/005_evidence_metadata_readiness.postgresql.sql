-- evidence_metadata: AI 분석 전 화질 적합성(readiness) 스냅샷
-- 적용: psql ... -f 005_evidence_metadata_readiness.postgresql.sql

ALTER TABLE evidence_metadata
    ADD COLUMN IF NOT EXISTS readiness_json JSONB;

COMMENT ON COLUMN evidence_metadata.readiness_json IS
    'Analysis readiness snapshot (FFPROBE instant / FRAME_SAMPLE). Tier, reasons, frame metrics.';
