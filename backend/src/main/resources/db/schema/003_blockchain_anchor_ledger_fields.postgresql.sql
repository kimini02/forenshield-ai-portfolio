-- PostgreSQL: blockchain_anchors ledger snapshot columns (clob is invalid on PG — use text)
-- Run once if Hibernate ddl-auto failed with: type "clob" does not exist

ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS error_code VARCHAR(50);
ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS signature_value TEXT;
ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS signer_certificate_hash VARCHAR(64);
ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS cert_verified BOOLEAN;
ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS offchain_log_hash VARCHAR(64);
ALTER TABLE blockchain_anchors ADD COLUMN IF NOT EXISTS offchain_ref_json TEXT;

ALTER TABLE evidence_manifests ADD COLUMN IF NOT EXISTS signer_certificate_hash VARCHAR(64);
