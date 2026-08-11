\set ON_ERROR_STOP on
\timing on

-- STEP 2C multi-key concurrency fixture.
-- Run only after seed-c2.sql in the isolated forenshield_c2 database.
-- The four users copy user_id=1's local-only password hash so no credential is stored here.
INSERT INTO users (
    user_id, login_id, email, password, name,
    organization_type, department, role, status,
    created_at, updated_at, deleted_at
)
SELECT
    test_user_id,
    format('c2-key-%s', test_user_id),
    format('c2-key-%s@test.invalid', test_user_id),
    source_user.password,
    format('C2 Key %s', test_user_id),
    'ETC',
    'Performance Test',
    'ROLE_USER',
    'APPROVED',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    NULL
FROM generate_series(2, 5) AS test_user_id
CROSS JOIN (SELECT password FROM users WHERE user_id = 1) AS source_user
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO evidences (
    evidence_id, uploader_id, case_number, case_name,
    file_name, file_type, mime_type, file_size,
    hash_algorithm, original_hash_value, original_storage_path,
    copy_status, status, lifecycle_status, uploaded_at, deleted_at
)
SELECT
    30000 + ((test_user_id - 2) * 1000) + evidence_no,
    test_user_id,
    format('C2-KEY-%s-CASE-%s', test_user_id, evidence_no),
    format('C2 Key %s Case %s', test_user_id, evidence_no),
    format('key-%s-evidence-%s.mp4', test_user_id, evidence_no),
    'VIDEO',
    'video/mp4',
    1048576,
    'SHA-256',
    lpad(to_hex(30000 + ((test_user_id - 2) * 1000) + evidence_no), 64, '0'),
    format('/isolated/c2/key-%s/evidence-%s.mp4', test_user_id, evidence_no),
    'NONE',
    'UPLOADED',
    'ACTIVE',
    TIMESTAMPTZ '2026-01-03 00:00:00+00' + evidence_no * INTERVAL '1 second',
    NULL
FROM generate_series(2, 5) AS test_user_id
CROSS JOIN generate_series(1, 1000) AS evidence_no
ON CONFLICT (evidence_id) DO NOTHING;

INSERT INTO analysis_requests (
    analysis_request_id, evidence_id, requested_by, status,
    requested_at, started_at, completed_at,
    error_code, error_message, progress_percent
)
SELECT
    90000 + ((((test_user_id - 2) * 1000) + evidence_no - 1) * 3) + request_no,
    30000 + ((test_user_id - 2) * 1000) + evidence_no,
    test_user_id,
    CASE
        WHEN request_no IN (1, 2) THEN 'FAILED'
        WHEN evidence_no % 4 = 0 THEN 'COMPLETED'
        WHEN evidence_no % 4 = 1 THEN 'QUEUED'
        WHEN evidence_no % 4 = 2 THEN 'ANALYZING'
        ELSE 'FAILED'
    END,
    TIMESTAMPTZ '2026-01-04 00:00:00+00'
        + ((((test_user_id - 2) * 1000 + evidence_no - 1) * 3) + request_no) * INTERVAL '1 second',
    CASE
        WHEN request_no = 3 AND evidence_no % 4 IN (0, 2)
        THEN TIMESTAMPTZ '2026-01-04 00:30:00+00' + evidence_no * INTERVAL '1 second'
        ELSE NULL
    END,
    CASE
        WHEN request_no = 3 AND evidence_no % 4 = 0
        THEN TIMESTAMPTZ '2026-01-04 01:00:00+00' + evidence_no * INTERVAL '1 second'
        ELSE NULL
    END,
    CASE
        WHEN request_no IN (1, 2) OR (request_no = 3 AND evidence_no % 4 = 3)
        THEN 'C2_MULTIKEY_FAILURE'
        ELSE NULL
    END,
    NULL,
    CASE
        WHEN request_no = 3 AND evidence_no % 4 = 0 THEN 100
        WHEN request_no = 3 AND evidence_no % 4 = 2 THEN 50
        ELSE 0
    END
FROM generate_series(2, 5) AS test_user_id
CROSS JOIN generate_series(1, 1000) AS evidence_no
CROSS JOIN generate_series(1, 3) AS request_no
ON CONFLICT (analysis_request_id) DO NOTHING;

INSERT INTO analysis_results (
    analysis_result_id, analysis_request_id,
    risk_score, confidence_score, risk_level, summary, analyzed_at
)
SELECT
    7500 + row_number() OVER (ORDER BY ar.analysis_request_id),
    ar.analysis_request_id,
    CASE ((((ar.evidence_id - 30001) % 1000) + 1) % 3)
        WHEN 0 THEN 90.0
        WHEN 1 THEN 60.0
        ELSE 20.0
    END,
    0.95,
    CASE ((((ar.evidence_id - 30001) % 1000) + 1) % 3)
        WHEN 0 THEN 'HIGH'
        WHEN 1 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    'C2 multi-key deterministic result',
    ar.completed_at
FROM analysis_requests ar
WHERE ar.analysis_request_id > 90000
  AND ar.status = 'COMPLETED'
ON CONFLICT (analysis_result_id) DO NOTHING;

SELECT setval('users_user_id_seq', (SELECT max(user_id) FROM users), true);
SELECT setval('evidences_evidence_id_seq', (SELECT max(evidence_id) FROM evidences), true);
SELECT setval(
    'analysis_requests_analysis_request_id_seq',
    (SELECT max(analysis_request_id) FROM analysis_requests),
    true
);
SELECT setval(
    'analysis_results_analysis_result_id_seq',
    (SELECT max(analysis_result_id) FROM analysis_results),
    true
);

ANALYZE users;
ANALYZE evidences;
ANALYZE analysis_requests;
ANALYZE analysis_results;

SELECT requested_by,
       count(*) AS total,
       count(*) FILTER (WHERE status = 'COMPLETED') AS completed,
       count(*) FILTER (WHERE status IN ('QUEUED', 'ANALYZING')) AS in_progress
FROM analysis_requests
WHERE requested_by BETWEEN 2 AND 5
GROUP BY requested_by
ORDER BY requested_by;
