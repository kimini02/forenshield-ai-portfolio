\set ON_ERROR_STOP on
\timing on

-- STEP 2A only: DB-level candidate. This is not wired into the application.
-- analysis_results.analysis_request_id is UNIQUE, so this LEFT JOIN cannot
-- multiply one AnalysisRequest into multiple aggregate input rows.

SELECT
    COUNT(*) AS total_analysis_count,
    COUNT(*) FILTER (
        WHERE ar.status = 'COMPLETED'
          AND r.risk_level IN ('HIGH', 'MEDIUM')
    ) AS deepfake_detected_count,
    COUNT(*) FILTER (
        WHERE ar.status = 'COMPLETED'
    ) AS completed_count,
    COUNT(*) FILTER (
        WHERE ar.status IN ('QUEUED', 'ANALYZING')
    ) AS in_progress_count
FROM analysis_requests ar
JOIN evidences e
  ON e.evidence_id = ar.evidence_id
LEFT JOIN analysis_results r
  ON r.analysis_request_id = ar.analysis_request_id
WHERE ar.requested_by = 1
  AND e.deleted_at IS NULL;

-- Equality gate: stop performance interpretation if any row is false.
WITH existing AS (
    SELECT
        (
            SELECT COUNT(ar.analysis_request_id)
            FROM analysis_requests ar
            JOIN evidences e ON e.evidence_id = ar.evidence_id
            WHERE ar.requested_by = 1
              AND e.deleted_at IS NULL
        ) AS total_analysis_count,
        (
            SELECT COUNT(ar.analysis_request_id)
            FROM analysis_requests ar
            JOIN evidences e ON e.evidence_id = ar.evidence_id
            JOIN analysis_results r
              ON r.analysis_request_id = ar.analysis_request_id
            WHERE ar.requested_by = 1
              AND e.deleted_at IS NULL
              AND ar.status = 'COMPLETED'
              AND r.risk_level IN ('HIGH', 'MEDIUM')
        ) AS deepfake_detected_count,
        (
            SELECT COUNT(ar.analysis_request_id)
            FROM analysis_requests ar
            JOIN evidences e ON e.evidence_id = ar.evidence_id
            WHERE ar.requested_by = 1
              AND e.deleted_at IS NULL
              AND ar.status = 'COMPLETED'
        ) AS completed_count,
        (
            SELECT COUNT(ar.analysis_request_id)
            FROM analysis_requests ar
            JOIN evidences e ON e.evidence_id = ar.evidence_id
            WHERE ar.requested_by = 1
              AND e.deleted_at IS NULL
              AND ar.status IN ('QUEUED', 'ANALYZING')
        ) AS in_progress_count
), aggregate_candidate AS (
    SELECT
        COUNT(*) AS total_analysis_count,
        COUNT(*) FILTER (
            WHERE ar.status = 'COMPLETED'
              AND r.risk_level IN ('HIGH', 'MEDIUM')
        ) AS deepfake_detected_count,
        COUNT(*) FILTER (WHERE ar.status = 'COMPLETED') AS completed_count,
        COUNT(*) FILTER (
            WHERE ar.status IN ('QUEUED', 'ANALYZING')
        ) AS in_progress_count
    FROM analysis_requests ar
    JOIN evidences e ON e.evidence_id = ar.evidence_id
    LEFT JOIN analysis_results r
      ON r.analysis_request_id = ar.analysis_request_id
    WHERE ar.requested_by = 1
      AND e.deleted_at IS NULL
)
SELECT
    metric,
    existing_value,
    aggregate_value,
    existing_value = aggregate_value AS is_equal
FROM existing e
CROSS JOIN aggregate_candidate a
CROSS JOIN LATERAL (
    VALUES
        ('totalAnalysisCount', e.total_analysis_count, a.total_analysis_count),
        ('deepfakeDetectedCount', e.deepfake_detected_count, a.deepfake_detected_count),
        ('completedCount', e.completed_count, a.completed_count),
        ('inProgressCount', e.in_progress_count, a.in_progress_count)
) result(metric, existing_value, aggregate_value)
ORDER BY metric;
