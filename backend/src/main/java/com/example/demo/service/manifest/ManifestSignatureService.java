package com.example.demo.service.manifest;

/**
 * RQ-REQ-050: ForenShield 단일 플랫폼 CA Manifest 전자서명.
 */
public interface ManifestSignatureService {

    String getSignatureAlgorithm();

    String getSignerCertificateSubject();

    /** Normalized PEM of the platform signer certificate (for signerCertHash). */
    String getSignerCertificatePem();

    String signManifest(String canonicalManifestJson);

    boolean verifyManifest(String canonicalManifestJson, String signatureBase64);
}
