\set ON_ERROR_STOP on
\timing off

-- One unrecorded warm-up for both alternatives. No setting is changed.
SELECT 'WARMUP EXISTING (not measured)' AS measurement;
SELECT
    (SELECT count(ar.analysis_request_id)
       FROM analysis_requests ar
       JOIN evidences e ON e.evidence_id = ar.evidence_id
      WHERE ar.requested_by = 1 AND e.deleted_at IS NULL),
    (SELECT count(ar.analysis_request_id)
       FROM analysis_requests ar
       JOIN evidences e ON e.evidence_id = ar.evidence_id
       JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
      WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
        AND ar.status = 'COMPLETED'
        AND r.risk_level IN ('HIGH', 'MEDIUM')),
    (SELECT count(ar.analysis_request_id)
       FROM analysis_requests ar
       JOIN evidences e ON e.evidence_id = ar.evidence_id
      WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
        AND ar.status = 'COMPLETED'),
    (SELECT count(ar.analysis_request_id)
       FROM analysis_requests ar
       JOIN evidences e ON e.evidence_id = ar.evidence_id
      WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
        AND ar.status IN ('QUEUED', 'ANALYZING'));

SELECT 'WARMUP AGGREGATE (not measured)' AS measurement;
SELECT
    COUNT(*),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED' AND r.risk_level IN ('HIGH', 'MEDIUM')),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED'),
    COUNT(*) FILTER (WHERE ar.status IN ('QUEUED', 'ANALYZING'))
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
LEFT JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL;

-- Run 1: existing first, aggregate second.
SELECT 'RUN 1 EXISTING Q1 totalAnalysisCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL;

SELECT 'RUN 1 EXISTING Q2 deepfakeDetectedCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
  AND ar.status = 'COMPLETED' AND r.risk_level IN ('HIGH', 'MEDIUM');

SELECT 'RUN 1 EXISTING Q3 completedCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL AND ar.status = 'COMPLETED';

SELECT 'RUN 1 EXISTING Q4 inProgressCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
  AND ar.status IN ('QUEUED', 'ANALYZING');

SELECT 'RUN 1 AGGREGATE' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    COUNT(*),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED' AND r.risk_level IN ('HIGH', 'MEDIUM')),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED'),
    COUNT(*) FILTER (WHERE ar.status IN ('QUEUED', 'ANALYZING'))
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
LEFT JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL;

-- Run 2: aggregate first to reduce order bias.
SELECT 'RUN 2 AGGREGATE' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    COUNT(*),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED' AND r.risk_level IN ('HIGH', 'MEDIUM')),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED'),
    COUNT(*) FILTER (WHERE ar.status IN ('QUEUED', 'ANALYZING'))
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
LEFT JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL;

SELECT 'RUN 2 EXISTING Q1 totalAnalysisCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL;

SELECT 'RUN 2 EXISTING Q2 deepfakeDetectedCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
  AND ar.status = 'COMPLETED' AND r.risk_level IN ('HIGH', 'MEDIUM');

SELECT 'RUN 2 EXISTING Q3 completedCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL AND ar.status = 'COMPLETED';

SELECT 'RUN 2 EXISTING Q4 inProgressCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
  AND ar.status IN ('QUEUED', 'ANALYZING');

-- Run 3: existing first, aggregate second.
SELECT 'RUN 3 EXISTING Q1 totalAnalysisCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL;

SELECT 'RUN 3 EXISTING Q2 deepfakeDetectedCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
  AND ar.status = 'COMPLETED' AND r.risk_level IN ('HIGH', 'MEDIUM');

SELECT 'RUN 3 EXISTING Q3 completedCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL AND ar.status = 'COMPLETED';

SELECT 'RUN 3 EXISTING Q4 inProgressCount' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(ar.analysis_request_id)
FROM analysis_requests ar JOIN evidences e ON e.evidence_id = ar.evidence_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL
  AND ar.status IN ('QUEUED', 'ANALYZING');

SELECT 'RUN 3 AGGREGATE' AS measurement;
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    COUNT(*),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED' AND r.risk_level IN ('HIGH', 'MEDIUM')),
    COUNT(*) FILTER (WHERE ar.status = 'COMPLETED'),
    COUNT(*) FILTER (WHERE ar.status IN ('QUEUED', 'ANALYZING'))
FROM analysis_requests ar
JOIN evidences e ON e.evidence_id = ar.evidence_id
LEFT JOIN analysis_results r ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1 AND e.deleted_at IS NULL;
