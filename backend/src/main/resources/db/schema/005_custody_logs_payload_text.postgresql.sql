-- Store CoC event payload as text so PostgreSQL does not reformat JSON whitespace
-- (which previously broke current_log_hash verification).

ALTER TABLE custody_logs
    ALTER COLUMN event_payload_json TYPE text
    USING event_payload_json::text;
