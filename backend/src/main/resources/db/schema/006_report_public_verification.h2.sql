ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS report_no VARCHAR(40);

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS verification_token VARCHAR(100);

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS verification_code VARCHAR(30);

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS public_access_code VARCHAR(30);

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS public_access_enabled BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS public_access_issued_at TIMESTAMP;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS public_access_expires_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_reports_report_no
    ON reports (report_no);

CREATE UNIQUE INDEX IF NOT EXISTS ux_reports_verification_token
    ON reports (verification_token);

CREATE UNIQUE INDEX IF NOT EXISTS ux_reports_verification_code
    ON reports (verification_code);

CREATE UNIQUE INDEX IF NOT EXISTS ux_reports_public_access_code
    ON reports (public_access_code);
