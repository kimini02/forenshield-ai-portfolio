\set ON_ERROR_STOP on

INSERT INTO users (
    login_id, email, password, name, organization_type, department,
    role, status, dark_mode, created_at, updated_at
) VALUES (
    :'investigator_login', :'investigator_email', :'password_hash', 'C4 Investigator',
    'POLICE', 'C4 Isolated', 'ROLE_INVESTIGATOR', 'APPROVED', false, now(), now()
)
RETURNING user_id AS investigator_id \gset

INSERT INTO users (
    login_id, email, password, name, organization_type, department,
    role, status, dark_mode, created_at, updated_at
) VALUES (
    :'reviewer_login', :'reviewer_email', :'password_hash', 'C4 Reviewer',
    'POLICE', 'C4 Isolated', 'ROLE_REVIEWER', 'APPROVED', false, now(), now()
)
RETURNING user_id AS reviewer_id \gset

INSERT INTO evidences (
    uploader_id, case_number, case_name, file_name, file_type, mime_type,
    file_size, hash_algorithm, original_hash_value, original_storage_path,
    copy_status, status, lifecycle_status, uploaded_at
) VALUES (
    :investigator_id, :'case_key', :'case_key', :'evidence_file', 'VIDEO', 'video/mp4',
    1024, 'SHA256', :'evidence_hash', :'evidence_path',
    'NONE', 'UPLOADED', 'ACTIVE', now()
)
RETURNING evidence_id \gset

INSERT INTO analysis_requests (
    evidence_id, requested_by, status, requested_at, started_at, completed_at, progress_percent
) VALUES (
    :evidence_id, :investigator_id, 'COMPLETED', now(), now(), now(), 100
)
RETURNING analysis_request_id \gset

INSERT INTO analysis_results (
    analysis_request_id, risk_score, confidence_score, risk_level, summary, analyzed_at
) VALUES (
    :analysis_request_id, 12.5, 98.0, 'LOW', 'C4 isolated Gatling fixture', now()
)
RETURNING analysis_result_id \gset

INSERT INTO case_profiles (
    uploader_id, case_key, representative_evidence_id, reviewer_id,
    review_status, review_requested_at, updated_at
) VALUES (
    :investigator_id, :'case_key', :evidence_id, :reviewer_id,
    'REVIEW_ASSIGNED', now(), now()
)
RETURNING case_profile_id \gset

\echo C4_FIXTURE case_key=:case_key reviewer_id=:reviewer_id analysis_result_id=:analysis_result_id
