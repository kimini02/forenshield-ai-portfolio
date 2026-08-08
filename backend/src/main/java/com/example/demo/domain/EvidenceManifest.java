package com.example.demo.domain;

import com.example.demo.domain.enums.SignatureStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_manifests")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceManifest {

    @Id
    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "manifest_json", nullable = false, columnDefinition = "clob")
    private String manifestJson;

    @Column(name = "manifest_hash", nullable = false, length = 64)
    private String manifestHash;

    @Column(name = "manifest_storage_path", columnDefinition = "clob")
    private String manifestStoragePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_status", nullable = false, length = 20)
    private SignatureStatus signatureStatus;

    @Column(name = "signature_algorithm", length = 50)
    private String signatureAlgorithm;

    @Column(name = "signature_value", columnDefinition = "clob")
    private String signatureValue;

    @Column(name = "signer_certificate_subject", length = 500)
    private String signerCertificateSubject;

    /** SHA-256 of normalized signer X.509 PEM (anchor-time fingerprint). */
    @Column(name = "signer_certificate_hash", length = 64)
    private String signerCertificateHash;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
