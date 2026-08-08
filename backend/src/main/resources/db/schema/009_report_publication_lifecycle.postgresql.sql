ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS publication_status VARCHAR(20) DEFAULT 'ISSUED' NOT NULL;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS report_version INTEGER DEFAULT 1 NOT NULL;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS issued_by BIGINT;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMP;

ALTER TABLE case_profiles
    ADD COLUMN IF NOT EXISTS review_approved_at TIMESTAMP;

UPDATE reports
SET publication_status = 'ISSUED'
WHERE publication_status IS NULL;

UPDATE reports
SET report_version = 1
WHERE report_version IS NULL;

UPDATE case_profiles
SET review_approved_at = updated_at
WHERE review_status = 'REPORT_APPROVED'
  AND review_approved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_reports_publication_status
    ON reports (publication_status);
