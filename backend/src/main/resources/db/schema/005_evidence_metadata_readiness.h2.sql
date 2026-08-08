-- H2 / local test (PostgreSQL MODE)
ALTER TABLE evidence_metadata
    ADD COLUMN IF NOT EXISTS readiness_json JSON;
