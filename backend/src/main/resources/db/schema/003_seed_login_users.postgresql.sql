-- 로컬/개발용 테스트 계정 (프론트 mock-auth와 동일)
-- 1111 / 2222 → ROLE_USER
-- 3333 / 4444 → ROLE_ADMIN
-- RDS에 적용: psql ... -f 003_seed_login_users.postgresql.sql

INSERT INTO users (
    login_id, email, password, name,
    organization_type, department, role, status,
    created_at, updated_at
) VALUES
(
    '1111',
    'user@test.local',
    '$2b$10$/vp/zlcJhCoOI5KWmQl.r.d7h61JCSil1cOBpHJsRZXG5FapXOGbK',
    '테스트 사용자',
    'ETC',
    '테스트부서',
    'ROLE_USER',
    'APPROVED',
    NOW(),
    NOW()
),
(
    '3333',
    'admin@test.local',
    '$2b$10$cSEHJUMM73D9Xiz2QQaEjei51XSZuE/jWraM6SelLySsaIQT1Eb/m',
    '테스트 관리자',
    'ETC',
    '운영팀',
    'ROLE_ADMIN',
    'APPROVED',
    NOW(),
    NOW()
)
ON CONFLICT (login_id) DO NOTHING;
