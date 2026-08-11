\set ON_ERROR_STOP on
\timing on

-- These statements are the PostgreSQL forms corresponding to the four
-- repository COUNT operations used by EvidenceStatsService on a cache MISS.

\echo 'Q1 totalAnalysisCount'
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1
  AND e.deleted_at IS NULL;

\echo 'Q2 deepfakeDetectedCount'
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1
  AND e.deleted_at IS NULL
  AND ar.status = 'COMPLETED'
  AND r.risk_level IN ('HIGH', 'MEDIUM');

\echo 'Q3 completedCount'
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1
  AND e.deleted_at IS NULL
  AND ar.status = 'COMPLETED';

\echo 'Q4 inProgressCount'
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1
  AND e.deleted_at IS NULL
  AND ar.status IN ('QUEUED', 'ANALYZING');
