-- =============================================================================
-- ForenShield — 초기 관리자 계정 (선택)
-- 비밀번호는 BCrypt 등으로 앱에서 해시한 값으로 교체하세요.
--
-- EC2 실행 예시:
--   psql -h localhost -U forenshield -d forenshield -f 002_seed_admin.postgresql.sql
-- =============================================================================

-- 이미 관리자가 있으면 스킵
INSERT INTO users (
    login_id,
    email,
    password,
    name,
    organization_type,
    department,
    role,
    status,
    created_at,
    updated_at
)
SELECT
    'admin',
    'admin@forenshield.local',
    '{bcrypt}$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH',
    '시스템 관리자',
    'ETC',
    '운영팀',
    'ROLE_ADMIN',
    'APPROVED',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE role = 'ROLE_ADMIN'
);
