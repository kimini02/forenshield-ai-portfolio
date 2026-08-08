package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicReportVerifyResponse {

    private final String status;
    private final boolean valid;
    private final String message;

    private final Long reportId;
    private final String reportNo;
    private final String verificationCode;
    private final String reportType;
    private final Integer revision;
    private final String publicationStatus;
    private final String issuedAt;
    private final String queriedAt;
    private final Boolean pdfSignatureApplied;
    private final Long evidenceId;
    private final String reportHash;
    private final String reportFileName;
    private final String createdAt;
    private final String analysisVerdict;
    private final String analysisCompletedAt;
    private final String snapshotSchemaVersion;
    private final String pdfTemplateVersion;

    private final Boolean hashMatched;
    private final Boolean storedFileIntact;
    private final Boolean signatureValid;
    private final String signatureStatus;
    private final String signatureAlgorithm;
    private final String signerCertificateSubject;
    private final Boolean evidenceManifestSignatureValid;
    private final String evidenceManifestSignatureStatus;
    private final String evidenceManifestSignatureAlgorithm;
    private final String evidenceManifestSignerCertificateSubject;

    private final Boolean blockchainMatched;
    private final String blockchainStatus;
    private final String blockchainTxHash;
    private final String blockchainNetwork;
    private final String blockchainAnchoredAt;
}
