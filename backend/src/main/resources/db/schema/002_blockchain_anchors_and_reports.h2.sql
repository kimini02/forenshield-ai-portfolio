-- H2 local file DB: compare PDF는 analysis_result_id 없이 저장됩니다.
-- 레거시 ddl-auto 스키마에서 NOT NULL 제약이 남아 있으면 compare PDF 저장이 500으로 실패합니다.

ALTER TABLE reports ALTER COLUMN analysis_result_id DROP NOT NULL;

ALTER TABLE reports ADD COLUMN IF NOT EXISTS compare_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_reports_compare_id ON reports (compare_id);
