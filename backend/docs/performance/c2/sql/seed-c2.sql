\set ON_ERROR_STOP on
\timing on

-- C2 dashboard stats cache Before measurement seed.
-- Run only against the isolated forenshield_c2 database.
TRUNCATE TABLE users RESTART IDENTITY CASCADE;

INSERT INTO users (
    user_id, login_id, email, password, name,
    organization_type, department, role, status,
    created_at, updated_at, deleted_at
) VALUES (
    1,
    'c2-user',
    'c2-user@test.invalid',
    '$2b$10$/vp/zlcJhCoOI5KWmQl.r.d7h61JCSil1cOBpHJsRZXG5FapXOGbK',
    'C2 Test User',
    'ETC',
    'Performance Test',
    'ROLE_USER',
    'APPROVED',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    NULL
);

SELECT setval('users_user_id_seq', 1, true);

INSERT INTO evidences (
    evidence_id, uploader_id, case_number, case_name,
    file_name, file_type, mime_type, file_size,
    hash_algorithm, original_hash_value, original_storage_path,
    copy_status, status, lifecycle_status, uploaded_at, deleted_at
)
SELECT
    evidence_no,
    1,
    format('C2-CASE-%s', ((evidence_no - 1) / 3) + 1),
    format('C2 Case %s', ((evidence_no - 1) / 3) + 1),
    format('evidence-%s.mp4', evidence_no),
    'VIDEO',
    'video/mp4',
    1048576,
    'SHA-256',
    lpad(to_hex(evidence_no), 64, '0'),
    format('/isolated/c2/evidence-%s.mp4', evidence_no),
    'NONE',
    'UPLOADED',
    'ACTIVE',
    TIMESTAMPTZ '2026-01-01 00:00:00+00' + evidence_no * INTERVAL '1 second',
    NULL
FROM generate_series(1, 30000) AS evidence_no;

SELECT setval('evidences_evidence_id_seq', 30000, true);

INSERT INTO analysis_requests (
    analysis_request_id, evidence_id, requested_by, status,
    requested_at, started_at, completed_at,
    error_code, error_message, progress_percent
)
SELECT
    ((evidence_no - 1) * 3) + request_no,
    evidence_no,
    1,
    CASE
        WHEN request_no IN (1, 2) THEN 'FAILED'
        WHEN evidence_no % 4 = 0 THEN 'COMPLETED'
        WHEN evidence_no % 4 = 1 THEN 'QUEUED'
        WHEN evidence_no % 4 = 2 THEN 'ANALYZING'
        ELSE 'FAILED'
    END,
    TIMESTAMPTZ '2026-01-02 00:00:00+00'
        + (((evidence_no - 1) * 3) + request_no) * INTERVAL '1 second',
    CASE
        WHEN request_no = 3 AND evidence_no % 4 IN (0, 2)
        THEN TIMESTAMPTZ '2026-01-02 00:30:00+00'
             + evidence_no * INTERVAL '1 second'
        ELSE NULL
    END,
    CASE
        WHEN request_no = 3 AND evidence_no % 4 = 0
        THEN TIMESTAMPTZ '2026-01-02 01:00:00+00'
             + evidence_no * INTERVAL '1 second'
        ELSE NULL
    END,
    CASE
        WHEN request_no IN (1, 2) OR (request_no = 3 AND evidence_no % 4 = 3)
        THEN 'C2_SEED_FAILURE'
        ELSE NULL
    END,
    NULL,
    CASE
        WHEN request_no = 3 AND evidence_no % 4 = 0 THEN 100
        WHEN request_no = 3 AND evidence_no % 4 = 2 THEN 50
        ELSE 0
    END
FROM generate_series(1, 30000) AS evidence_no
CROSS JOIN generate_series(1, 3) AS request_no;

SELECT setval('analysis_requests_analysis_request_id_seq', 90000, true);

INSERT INTO analysis_results (
    analysis_result_id, analysis_request_id,
    risk_score, confidence_score, risk_level, summary, analyzed_at
)
SELECT
    row_number() OVER (ORDER BY ar.analysis_request_id),
    ar.analysis_request_id,
    CASE ar.evidence_id % 3
        WHEN 0 THEN 90.0
        WHEN 1 THEN 60.0
        ELSE 20.0
    END,
    0.95,
    CASE ar.evidence_id % 3
        WHEN 0 THEN 'HIGH'
        WHEN 1 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    'C2 deterministic result',
    ar.completed_at
FROM analysis_requests ar
WHERE ar.status = 'COMPLETED';

SELECT setval('analysis_results_analysis_result_id_seq', 7500, true);

ANALYZE users;
ANALYZE evidences;
ANALYZE analysis_requests;
ANALYZE analysis_results;

SELECT 'users' AS metric, count(*) AS value FROM users
UNION ALL SELECT 'evidences', count(*) FROM evidences
UNION ALL SELECT 'deleted_evidences', count(*) FROM evidences WHERE deleted_at IS NOT NULL
UNION ALL SELECT 'analysis_requests', count(*) FROM analysis_requests
UNION ALL SELECT 'analysis_results', count(*) FROM analysis_results;

SELECT status AS metric, count(*) AS value
FROM analysis_requests
GROUP BY status
ORDER BY status;

SELECT risk_level AS metric, count(*) AS value
FROM analysis_results
GROUP BY risk_level
ORDER BY risk_level;
