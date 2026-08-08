package com.example.demo.service.integrity;

import com.example.demo.service.evidence.EvidenceCopyService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.NotificationType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.SecurityAlertCode;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceManifestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class IntegrityVerificationServiceTest {

    @Autowired
    private IntegrityVerificationService integrityVerificationService;

    @Autowired
    private EvidenceCopyService evidenceCopyService;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private EvidenceManifestRepository evidenceManifestRepository;

    @Autowired
    private BlockchainAnchorRepository blockchainAnchorRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client;

    private User user;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        custodyLogRepository.deleteAll();
        evidenceManifestRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .loginId("integrity-user")
                .email("integrity@test.dev")
                .password("encoded")
                .name("Integrity User")
                .organizationType(OrgType.ETC)
                .department("test")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
    }

    @Test
    @DisplayName("RQ-SEC-153: Manifest 서명 검증 실패 시 SECURITY_ALERT 알림을 생성한다")
    void verifyAndNotify_invalidSignature_createsSecurityAlert() {
        Evidence evidence = saveEvidenceWithCopy("tampered-sig.mp4");
        EvidenceManifest manifest = evidenceManifestRepository.findById(evidence.getEvidenceId()).orElseThrow();
        manifest.setSignatureValue("invalid-signature");
        evidenceManifestRepository.save(manifest);

        EvidenceIntegrityResult result = integrityVerificationService.verifyAndNotifySecurityIssues(
                user, evidence.getEvidenceId());
        IntegrityVerifyResponse response = result.verification();

        assertThat(response.isValid()).isFalse();
        assertThat(response.getChecks())
                .anyMatch(check -> "SIGNATURE".equals(check.getCheckType()) && !check.isValid());

        List<com.example.demo.domain.Notification> alerts = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.SECURITY_ALERT)
                .toList();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getReferenceType())
                .isEqualTo("SEC:SIG_INVALID");
    }

    @Test
    @DisplayName("RQ-SEC-153: 블록체인 해시 불일치 시 SECURITY_ALERT 알림을 생성한다")
    void verifyAndNotify_blockchainMismatch_createsSecurityAlert() {
        Evidence evidence = saveEvidenceWithCopy("blockchain-mismatch.mp4");

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash("d".repeat(64));
        anchor.setEvidenceId(evidence.getEvidenceId());
        anchor.setCreatedBy(user.getUserId());
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setTransactionHash("0xbad");
        anchor.setNetwork("local-simulated");
        anchor.setAnchoredAt(LocalDateTime.now());
        anchor.setCreatedAt(LocalDateTime.now());
        blockchainAnchorRepository.save(anchor);

        EvidenceIntegrityResult result = integrityVerificationService.verifyAndNotifySecurityIssues(
                user, evidence.getEvidenceId());
        IntegrityVerifyResponse response = result.verification();

        assertThat(response.isValid()).isFalse();
        assertThat(notificationRepository.findAll())
                .anyMatch(n -> n.getType() == NotificationType.SECURITY_ALERT
                        && n.getReferenceType().equals("SEC:BC_MISMATCH"));
    }

    @Test
    @DisplayName("RQ-SEC-153: CoC 체인 불일치 시 SECURITY_ALERT 알림을 생성한다")
    void verifyAndNotify_brokenChain_createsSecurityAlert() {
        Evidence evidence = saveEvidenceWithCopy("broken-chain.mp4");

        CustodyLog log = new CustodyLog();
        log.setTargetType(CustodyTargetType.EVIDENCE);
        log.setTargetId(evidence.getEvidenceId());
        log.setActorId(user.getUserId());
        log.setActionType("FILE_UPLOADED");
        log.setPreviousLogHash("genesis");
        log.setCurrentLogHash("broken-hash-value");
        log.setCreatedAt(LocalDateTime.now());
        custodyLogRepository.save(log);

        EvidenceIntegrityResult result = integrityVerificationService.verifyAndNotifySecurityIssues(
                user, evidence.getEvidenceId());
        IntegrityVerifyResponse response = result.verification();

        assertThat(response.isValid()).isFalse();
        assertThat(notificationRepository.findAll())
                .anyMatch(n -> n.getType() == NotificationType.SECURITY_ALERT
                        && n.getReferenceType().equals("SEC:CHAIN_FAIL"));
    }

    @Test
    @DisplayName("SK-632: 무결성 검증 API는 실패 시 errorCode를 반환한다")
    void verifyIntegrityOrThrow_throwsOnFailure() {
        Evidence evidence = saveEvidenceWithCopy("verify-throw.mp4");
        EvidenceManifest manifest = evidenceManifestRepository.findById(evidence.getEvidenceId()).orElseThrow();
        manifest.setSignatureValue("tampered");
        evidenceManifestRepository.save(manifest);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.example.demo.exception.BusinessException.class,
                () -> integrityVerificationService.verifyIntegrityOrThrow(user, evidence.getEvidenceId())
        );
    }

    private Evidence saveEvidenceWithCopy(String fileName) {
        byte[] fileBytes = ("video-" + fileName).getBytes(StandardCharsets.UTF_8);
        String originalKey = "cases/sec-case/1/original/" + fileName;
        s3Client.putObject(
                PutObjectRequest.builder().bucket("test-evidence-bucket").key(originalKey).build(),
                RequestBody.fromBytes(fileBytes)
        );

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("sec-case")
                .caseNumber("sec-case")
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize((long) fileBytes.length)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(new HashService().generateSha256(fileBytes))
                .originalStoragePath(originalKey)
                .uploadedAt(LocalDateTime.now())
                .build());

        evidenceCopyService.prepareCopyForAnalysis(evidence, user.getUserId());
        assertThat(evidence.getCopyStatus()).isEqualTo(CopyStatus.ACTIVE);
        return evidenceRepository.findById(evidence.getEvidenceId()).orElseThrow();
    }
}
