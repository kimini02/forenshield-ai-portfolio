-- Test/local schema parity for the AnalysisResult-to-ReportIssueTask business invariant.
ALTER TABLE report_issue_tasks
    ADD CONSTRAINT IF NOT EXISTS uq_report_issue_tasks_analysis_result_id UNIQUE (analysis_result_id);
