-- H2: blockchain anchor analysis snapshot columns for ledger anchoring

ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS analysis_model_json CLOB;
ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS analysis_modules_json CLOB;
