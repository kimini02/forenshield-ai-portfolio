-- RQ-REQ-050: Evidence Manifest + 플랫폼 X.509(PKCS#8) 서명 메타데이터
CREATE TABLE IF NOT EXISTS evidence_manifests (
    evidence_id                 BIGINT PRIMARY KEY REFERENCES evidences (evidence_id) ON DELETE CASCADE,
    manifest_json               TEXT          NOT NULL,
    manifest_hash               VARCHAR(64)   NOT NULL,
    manifest_storage_path       TEXT,
    signature_status            VARCHAR(20)   NOT NULL,
    signature_algorithm         VARCHAR(50),
    signature_value             TEXT,
    signer_certificate_subject  VARCHAR(500),
    signed_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_evidence_manifests_signature_status
        CHECK (signature_status IN ('SIGNED', 'UNSIGNED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_evidence_manifests_created_at ON evidence_manifests (created_at);
