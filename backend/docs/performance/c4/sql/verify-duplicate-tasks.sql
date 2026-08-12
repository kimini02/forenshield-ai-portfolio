-- Summary: any AnalysisResult that owns more than one durable report issue task.
SELECT
    analysis_result_id,
    COUNT(*) AS duplicate_count
FROM report_issue_tasks
GROUP BY analysis_result_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, analysis_result_id;

-- Detail: replace :analysisResultId in DBeaver, or use \set with psql.
SELECT
    report_issue_task_id,
    analysis_result_id,
    evidence_id,
    status,
    requested_by,
    attempt_count,
    created_at,
    started_at,
    completed_at
FROM report_issue_tasks
WHERE analysis_result_id = :analysisResultId
ORDER BY report_issue_task_id;
