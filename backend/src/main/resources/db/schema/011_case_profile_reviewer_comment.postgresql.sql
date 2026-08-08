-- 운영 환경은 ddl-auto=validate이므로 배포 전 적용합니다.
ALTER TABLE case_profiles
    ADD COLUMN IF NOT EXISTS reviewer_comment VARCHAR(500);
