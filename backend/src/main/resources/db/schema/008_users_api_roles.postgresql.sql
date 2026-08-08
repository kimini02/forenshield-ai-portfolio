-- users.role API roles
-- Apply once to existing PostgreSQL/RDS databases before deploying code that approves
-- INVESTIGATOR, REVIEWER, or ORG_ADMIN accounts.

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
    CHECK (
        role IN (
            'ROLE_USER',
            'ROLE_ADMIN',
            'ROLE_INVESTIGATOR',
            'ROLE_REVIEWER',
            'ROLE_ORG_ADMIN'
        )
    );
