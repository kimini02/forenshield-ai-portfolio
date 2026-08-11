\set ON_ERROR_STOP on

TRUNCATE TABLE analysis_results, analysis_requests, case_profiles, evidences, users
RESTART IDENTITY CASCADE;

INSERT INTO users (
    user_id, dark_mode, created_at, updated_at, role, status,
    organization_type, login_id, name, department, email, password
) VALUES
    (1, false, clock_timestamp(), clock_timestamp(), 'ROLE_INVESTIGATOR', 'APPROVED',
     'POLICE', 'baseline-investigator', 'Baseline Investigator', 'Digital Forensics',
     'investigator@baseline.local', :'app_password_hash'),
    (2, false, clock_timestamp(), clock_timestamp(), 'ROLE_ORG_ADMIN', 'APPROVED',
     'POLICE', 'baseline-org-admin', 'Baseline Org Admin', 'Digital Forensics',
     'org-admin@baseline.local', :'app_password_hash'),
    (3, false, clock_timestamp(), clock_timestamp(), 'ROLE_REVIEWER', 'APPROVED',
     'POLICE', 'baseline-reviewer', 'Baseline Reviewer', 'Digital Forensics',
     'reviewer@baseline.local', :'app_password_hash');

WITH generated AS (
    SELECT c.case_no, e.evidence_no,
           ((c.case_no - 1) * :evidence_per + e.evidence_no)::bigint AS evidence_id
    FROM generate_series(1, :cases) AS c(case_no)
    CROSS JOIN generate_series(1, :evidence_per) AS e(evidence_no)
)
INSERT INTO evidences (
    evidence_id, file_size, uploaded_at, uploader_id, copy_status,
    evidence_role, file_type, hash_algorithm, lifecycle_status, status,
    original_hash_value, case_number, display_label, mime_type, file_name,
    case_name, original_storage_path
)
SELECT evidence_id,
       1048576 + evidence_no,
       clock_timestamp() - ((:cases - case_no) * interval '1 millisecond')
                         + (evidence_no * interval '1 microsecond'),
       1,
       'NONE',
       CASE WHEN evidence_no = 1 THEN 'PRIMARY' ELSE 'SUPPLEMENT' END,
       'VIDEO', 'SHA-256', 'ACTIVE', 'UPLOADED',
       lpad(to_hex(evidence_id), 64, '0'),
       'CASE-' || lpad(case_no::text, 6, '0'),
       'Evidence ' || evidence_no,
       'video/mp4',
       'case-' || lpad(case_no::text, 6, '0') || '-e' || evidence_no || '.mp4',
       'Baseline Case ' || lpad(case_no::text, 6, '0'),
       's3://baseline/evidence/' || evidence_id
FROM generated;

WITH generated AS (
    SELECT e.evidence_id,
           ((e.evidence_id - 1) * :requests_per + r.request_no)::bigint AS request_id,
           r.request_no,
           ((e.evidence_id - 1) / :evidence_per + 1)::integer AS case_no
    FROM evidences e
    CROSS JOIN generate_series(1, :requests_per) AS r(request_no)
)
INSERT INTO analysis_requests (
    analysis_request_id, progress_percent, completed_at, evidence_id,
    requested_at, requested_by, started_at, status, error_code, error_message
)
SELECT request_id,
       CASE
           WHEN request_no < :requests_per THEN 100
           WHEN case_no % 4 = 0 THEN 100
           WHEN case_no % 4 = 3 THEN 40
           ELSE 0
       END,
       CASE
           WHEN request_no < :requests_per THEN clock_timestamp()
           WHEN case_no % 4 = 0 THEN clock_timestamp()
           ELSE NULL
       END,
       evidence_id,
       clock_timestamp() - ((:requests_per - request_no) * interval '1 second'),
       1,
       CASE
           WHEN request_no < :requests_per OR case_no % 4 IN (0, 3) THEN clock_timestamp()
           ELSE NULL
       END,
       CASE
           WHEN request_no < :requests_per THEN 'FAILED'
           WHEN case_no % 4 = 0 THEN 'COMPLETED'
           WHEN case_no % 4 = 1 THEN 'QUEUED'
           WHEN case_no % 4 = 2 THEN 'FAILED'
           ELSE 'ANALYZING'
       END,
       CASE WHEN request_no < :requests_per OR case_no % 4 = 2 THEN 'BASELINE_FAILURE' ELSE NULL END,
       CASE WHEN request_no < :requests_per OR case_no % 4 = 2 THEN 'seeded failure' ELSE NULL END
FROM generated;

INSERT INTO analysis_results (
    analysis_request_id, analyzed_at, confidence_score, risk_score, risk_level, summary
)
SELECT ar.analysis_request_id,
       ar.completed_at,
       0.80,
       ((ar.evidence_id % 100)::double precision / 100.0),
       CASE
           WHEN ar.evidence_id % 3 = 0 THEN 'HIGH'
           WHEN ar.evidence_id % 3 = 1 THEN 'MEDIUM'
           ELSE 'LOW'
       END,
       'baseline result'
FROM analysis_requests ar
WHERE ar.status = 'COMPLETED';

INSERT INTO case_profiles (
    representative_evidence_id, reviewer_id, updated_at, uploader_id,
    review_status, case_key
)
SELECT ((case_no - 1) * :evidence_per + 1)::bigint,
       3,
       clock_timestamp(),
       1,
       'REVIEW_ASSIGNED',
       'CASE-' || lpad(case_no::text, 6, '0')
FROM generate_series(1, :cases) AS c(case_no);

SELECT setval(pg_get_serial_sequence('users', 'user_id'), (SELECT max(user_id) FROM users), true);
SELECT setval(pg_get_serial_sequence('evidences', 'evidence_id'), (SELECT max(evidence_id) FROM evidences), true);
SELECT setval(pg_get_serial_sequence('analysis_requests', 'analysis_request_id'), (SELECT max(analysis_request_id) FROM analysis_requests), true);
SELECT setval(pg_get_serial_sequence('analysis_results', 'analysis_result_id'), (SELECT max(analysis_result_id) FROM analysis_results), true);
SELECT setval(pg_get_serial_sequence('case_profiles', 'case_profile_id'), (SELECT max(case_profile_id) FROM case_profiles), true);

ANALYZE users;
ANALYZE evidences;
ANALYZE analysis_requests;
ANALYZE analysis_results;
ANALYZE case_profiles;
