-- 로컬 H2는 ddl-auto=update로 자동 반영되지만 수동 초기화에도 사용합니다.
ALTER TABLE case_profiles
    ADD COLUMN IF NOT EXISTS reviewer_comment VARCHAR(500);
