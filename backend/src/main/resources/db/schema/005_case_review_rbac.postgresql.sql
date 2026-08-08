-- RBAC phase 2 + case review metadata for mypage CaseSummary fields
-- Apply after 004_case_profiles_review_status.postgresql.sql

ALTER TABLE case_profiles
    ADD COLUMN IF NOT EXISTS assignee_id BIGINT,
    ADD COLUMN IF NOT EXISTS reviewer_id BIGINT,
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(40) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS review_requested_at TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS review_request_memo VARCHAR(500);

UPDATE case_profiles
SET assignee_id = uploader_id
WHERE assignee_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_case_profiles_reviewer_id ON case_profiles (reviewer_id);
CREATE INDEX IF NOT EXISTS idx_case_profiles_review_status ON case_profiles (review_status);
