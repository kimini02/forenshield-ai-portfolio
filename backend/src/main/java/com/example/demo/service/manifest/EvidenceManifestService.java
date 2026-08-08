package com.example.demo.service.manifest;

import com.example.demo.service.blockchain.SignerCertificateHashCalculator;
import com.example.demo.service.evidence.EvidenceStoragePaths;
import com.example.demo.service.evidence.HashService;
import com.example.demo.config.EvidenceManifestProperties;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.repository.EvidenceManifestRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceManifestService {

    private static final String MANIFEST_VERSION = "1.0";

    private final EvidenceManifestRepository evidenceManifestRepository;
    private final HashService hashService;
    private final ManifestSignatureService manifestSignatureService;
    private final SignerCertificateHashCalculator signerCertificateHashCalculator;
    private final EvidenceManifestProperties manifestProperties;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    @Transactional
    public EvidenceManifest ensureManifest(Evidence evidence) {
        return evidenceManifestRepository.findById(evidence.getEvidenceId())
                .orElseGet(() -> createAndSignManifest(evidence));
    }

    @Transactional
    public EvidenceManifest createAndSignManifest(Evidence evidence) {
        Optional<EvidenceManifest> existing = evidenceManifestRepository.findById(evidence.getEvidenceId());
        if (existing.isPresent()) {
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> manifestBody = buildManifestBody(evidence, now);
        String manifestJson = toCanonicalJson(manifestBody);
        String manifestHash = hashService.generateSha256(manifestJson.getBytes(StandardCharsets.UTF_8));

        String caseKey = EvidenceStoragePaths.resolveCaseKey(evidence);
        String manifestKey = EvidenceStoragePaths.manifestKey(caseKey, evidence.getEvidenceId());

        SignatureStatus signatureStatus = SignatureStatus.UNSIGNED;
        String signatureAlgorithm = null;
        String signatureValue = null;
        String signerSubject = null;
        String signerCertificateHash = null;
        LocalDateTime signedAt = null;

        try {
            signatureValue = manifestSignatureService.signManifest(manifestJson);
            signatureAlgorithm = manifestSignatureService.getSignatureAlgorithm();
            signerSubject = manifestSignatureService.getSignerCertificateSubject();
            signerCertificateHash = signerCertificateHashCalculator.hashPem(
                    manifestSignatureService.getSignerCertificatePem());
            signedAt = now;
            signatureStatus = SignatureStatus.SIGNED;
        } catch (Exception ex) {
            log.warn("Manifest X.509 signing failed evidenceId={}", evidence.getEvidenceId(), ex);
            signatureStatus = SignatureStatus.FAILED;
        }

        uploadManifestToS3(manifestKey, manifestJson);

        EvidenceManifest manifest = new EvidenceManifest();
        manifest.setEvidenceId(evidence.getEvidenceId());
        manifest.setManifestJson(manifestJson);
        manifest.setManifestHash(manifestHash);
        manifest.setManifestStoragePath(manifestKey);
        manifest.setSignatureStatus(signatureStatus);
        manifest.setSignatureAlgorithm(signatureAlgorithm);
        manifest.setSignatureValue(signatureValue);
        manifest.setSignerCertificateSubject(signerSubject);
        manifest.setSignerCertificateHash(signerCertificateHash);
        manifest.setSignedAt(signedAt);
        manifest.setCreatedAt(now);

        EvidenceManifest saved = evidenceManifestRepository.save(manifest);
        log.info("Evidence manifest created evidenceId={} status={}", evidence.getEvidenceId(), signatureStatus);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<EvidenceManifest> findByEvidenceId(Long evidenceId) {
        return evidenceManifestRepository.findById(evidenceId);
    }

    public boolean isSignatureValid(EvidenceManifest manifest) {
        if (manifest.getSignatureStatus() != SignatureStatus.SIGNED
                || manifest.getSignatureValue() == null
                || manifest.getManifestJson() == null) {
            return false;
        }
        return manifestSignatureService.verifyManifest(manifest.getManifestJson(), manifest.getSignatureValue());
    }

    private Map<String, Object> buildManifestBody(Evidence evidence, LocalDateTime issuedAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("manifestVersion", MANIFEST_VERSION);
        body.put("evidenceId", evidence.getEvidenceId());
        body.put("fileId", evidence.getEvidenceId());
        body.put("caseId", EvidenceCaseIdResolver.resolve(evidence));
        body.put("caseNumber", evidence.getCaseNumber());
        body.put("caseName", evidence.getCaseName());
        body.put("fileName", evidence.getFileName());
        body.put("fileType", evidence.getFileType().name());
        body.put("uploadedAt", ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()));
        body.put("hashAlgorithm", evidence.getHashAlgorithm());
        body.put("originalHash", evidence.getOriginalHashValue());
        body.put("originalSha256", evidence.getOriginalHashValue());
        body.put("copyHash", evidence.getCopyHashValue());
        body.put("copyStoragePath", evidence.getCopyStoragePath());
        body.put("issuer", manifestProperties.getIssuer());
        body.put("signer", manifestProperties.getIssuer());
        body.put("issuedAt", ApiDateTimeFormatter.formatUtc(issuedAt));
        return body;
    }

    private String toCanonicalJson(Map<String, Object> manifestBody) {
        try {
            return objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(manifestBody);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Manifest JSON serialization failed", ex);
        }
    }

    private void uploadManifestToS3(String manifestKey, String manifestJson) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(evidenceBucket)
                        .key(manifestKey)
                        .contentType("application/json")
                        .build(),
                RequestBody.fromString(manifestJson, StandardCharsets.UTF_8)
        );
    }
}
