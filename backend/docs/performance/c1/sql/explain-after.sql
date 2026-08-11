\set ON_ERROR_STOP on
\pset pager off

CREATE OR REPLACE TEMP VIEW c1_candidates AS
WITH latest_request_ranked AS (
    SELECT ar.evidence_id,
           ar.status,
           ROW_NUMBER() OVER (
               PARTITION BY ar.evidence_id
               ORDER BY ar.requested_at DESC, ar.analysis_request_id DESC
           ) AS request_rank
    FROM analysis_requests ar
),
case_evidence AS (
    SELECT e.evidence_id,
           e.uploader_id AS owner_id,
           CASE
               WHEN e.case_number IS NOT NULL AND TRIM(e.case_number) <> '' THEN e.case_number
               WHEN e.case_name IS NOT NULL AND TRIM(e.case_name) <> '' THEN e.case_name
               ELSE CONCAT('EVIDENCE-', e.evidence_id)
           END AS case_key,
           e.case_name,
           e.display_label,
           e.lifecycle_status,
           e.uploaded_at,
           CASE
               WHEN lr.status = 'ANALYZING' THEN 0
               WHEN lr.status IS NULL OR lr.status = 'QUEUED' THEN 1
               WHEN lr.status = 'FAILED' THEN 2
               ELSE 3
           END AS status_priority
    FROM evidences e
    LEFT JOIN latest_request_ranked lr
      ON lr.evidence_id = e.evidence_id
     AND lr.request_rank = 1
    WHERE e.status = 'UPLOADED'
      AND e.deleted_at IS NULL
),
positioned_evidence AS (
    SELECT ce.*,
           ROW_NUMBER() OVER (
               PARTITION BY ce.owner_id, ce.case_key
               ORDER BY ce.evidence_id
           ) AS evidence_position
    FROM case_evidence ce
),
evidence_aggregates AS (
    SELECT owner_id,
           case_key,
           MIN(uploaded_at) AS created_at,
           MIN(status_priority) AS status_priority
    FROM positioned_evidence
    GROUP BY owner_id, case_key
),
representative_ranked AS (
    SELECT pe.owner_id,
           pe.case_key,
           pe.evidence_id,
           pe.case_name,
           pe.display_label,
           pe.evidence_position,
           ROW_NUMBER() OVER (
               PARTITION BY pe.owner_id, pe.case_key
               ORDER BY
                   CASE
                       WHEN cp.representative_evidence_id = pe.evidence_id THEN 0
                       WHEN pe.lifecycle_status = 'ACTIVE' THEN 1
                       ELSE 2
                   END,
                   pe.evidence_id
           ) AS representative_rank
    FROM positioned_evidence pe
    LEFT JOIN case_profiles cp
      ON cp.uploader_id = pe.owner_id
     AND cp.case_key = pe.case_key
),
evidence_candidates AS (
    SELECT ea.owner_id,
           ea.case_key,
           ea.created_at,
           ea.status_priority,
           CASE ea.status_priority
               WHEN 0 THEN 'PROCESSING'
               WHEN 1 THEN 'PENDING'
               WHEN 2 THEN 'FAILED'
               ELSE 'COMPLETED'
           END AS case_status,
           COALESCE(NULLIF(TRIM(rr.case_name), ''), ea.case_key) AS search_case_name,
           CASE
               WHEN rr.display_label IS NOT NULL AND TRIM(rr.display_label) <> ''
                   THEN rr.display_label
               ELSE CONCAT('증거 ', rr.evidence_position)
           END AS representative_label,
           rr.evidence_id AS representative_evidence_id,
           cp.reviewer_id
    FROM evidence_aggregates ea
    JOIN representative_ranked rr
      ON rr.owner_id = ea.owner_id
     AND rr.case_key = ea.case_key
     AND rr.representative_rank = 1
    LEFT JOIN case_profiles cp
      ON cp.uploader_id = ea.owner_id
     AND cp.case_key = ea.case_key
),
profile_candidates AS (
    SELECT cp.uploader_id AS owner_id,
           cp.case_key,
           cp.updated_at AS created_at,
           1 AS status_priority,
           'PENDING' AS case_status,
           cp.case_key AS search_case_name,
           CAST(NULL AS VARCHAR(255)) AS representative_label,
           CAST(NULL AS BIGINT) AS representative_evidence_id,
           cp.reviewer_id
    FROM case_profiles cp
    WHERE NOT EXISTS (
        SELECT 1
        FROM case_evidence ce
        WHERE ce.owner_id = cp.uploader_id
          AND ce.case_key = cp.case_key
    )
),
candidates AS (
    SELECT * FROM evidence_candidates
    UNION ALL
    SELECT * FROM profile_candidates
)
SELECT * FROM candidates;

\echo '=== PAGE CONTENT: owner=1, newest, offset=0, size=10 ==='
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT owner_id, case_key
FROM c1_candidates
WHERE owner_id = 1
ORDER BY created_at DESC, owner_id ASC, case_key ASC
OFFSET 0 ROWS FETCH FIRST 10 ROWS ONLY;

\echo '=== PAGE CONTENT: owner=1, newest, offset=5000, size=10 ==='
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT owner_id, case_key
FROM c1_candidates
WHERE owner_id = 1
ORDER BY created_at DESC, owner_id ASC, case_key ASC
OFFSET 5000 ROWS FETCH FIRST 10 ROWS ONLY;

\echo '=== PAGE CONTENT: owner=1, newest, offset=9990, size=10 ==='
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT owner_id, case_key
FROM c1_candidates
WHERE owner_id = 1
ORDER BY created_at DESC, owner_id ASC, case_key ASC
OFFSET 9990 ROWS FETCH FIRST 10 ROWS ONLY;

\echo '=== PAGE COUNT: owner=1 ==='
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(*)
FROM c1_candidates
WHERE owner_id = 1;

\echo '=== EVIDENCE BATCH: newest page 10 case keys ==='
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT e.*
FROM evidences e
WHERE e.status = 'UPLOADED'
  AND e.deleted_at IS NULL
  AND e.uploader_id = 1
  AND CASE
          WHEN e.case_number IS NOT NULL AND TRIM(e.case_number) <> '' THEN e.case_number
          WHEN e.case_name IS NOT NULL AND TRIM(e.case_name) <> '' THEN e.case_name
          ELSE CONCAT('EVIDENCE-', e.evidence_id)
      END IN (
          'CASE-010000', 'CASE-009999', 'CASE-009998', 'CASE-009997', 'CASE-009996',
          'CASE-009995', 'CASE-009994', 'CASE-009993', 'CASE-009992', 'CASE-009991'
      )
ORDER BY e.evidence_id;

\echo '=== ANALYSIS REQUEST BATCH: newest page 30 evidence ids ==='
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT ar.*
FROM analysis_requests ar
WHERE ar.evidence_id BETWEEN 29971 AND 30000
ORDER BY ar.requested_at DESC, ar.analysis_request_id DESC;

\echo '=== CASE PROFILE BATCH: newest page 10 case keys ==='
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT cp.*
FROM case_profiles cp
WHERE cp.uploader_id = 1
  AND cp.case_key IN (
      'CASE-010000', 'CASE-009999', 'CASE-009998', 'CASE-009997', 'CASE-009996',
      'CASE-009995', 'CASE-009994', 'CASE-009993', 'CASE-009992', 'CASE-009991'
  );
