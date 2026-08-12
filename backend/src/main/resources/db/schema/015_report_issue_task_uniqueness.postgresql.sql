-- Preflight: any returned row requires business reconciliation before this migration is applied.
-- This migration intentionally does not choose or delete a duplicate task automatically.
SELECT
    analysis_result_id,
    COUNT(*)
FROM report_issue_tasks
GROUP BY analysis_result_id
HAVING COUNT(*) > 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM report_issue_tasks
        GROUP BY analysis_result_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate report_issue_tasks require reconciliation before adding analysis_result uniqueness';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_report_issue_tasks_analysis_result_id'
          AND conrelid = 'report_issue_tasks'::regclass
    ) THEN
        ALTER TABLE report_issue_tasks
            ADD CONSTRAINT uq_report_issue_tasks_analysis_result_id UNIQUE (analysis_result_id);
    END IF;
END
$$;
