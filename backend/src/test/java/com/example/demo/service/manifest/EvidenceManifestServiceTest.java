package com.example.demo.service.manifest;

import com.example.demo.service.evidence.EvidenceCopyService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.EvidenceManifestRepository;
import com.example.demo.repository.EvidenceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class EvidenceManifestServiceTest {

    @Autowired
    private EvidenceManifestService evidenceManifestService;

    @Autowired
    private EvidenceManifestRepository evidenceManifestRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private EvidenceCopyService evidenceCopyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        evidenceManifestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("RQ-REQ-050: 분석 사본 생성 시 Manifest가 생성되고 플랫폼 X.509로 서명된다")
    void prepareCopy_createsSignedManifest() {
        User user = userRepository.save(User.builder()
                .loginId("manifest-user")
                .email("manifest@test.dev")
                .password("encoded")
                .name("Manifest User")
                .organizationType(OrgType.ETC)
                .department("test")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        byte[] fileBytes = "manifest test video".getBytes(StandardCharsets.UTF_8);
        String originalKey = "cases/manifest-case/1/original/sample.mp4";
        s3Client.putObject(
                PutObjectRequest.builder().bucket("test-evidence-bucket").key(originalKey).build(),
                RequestBody.fromBytes(fileBytes)
        );

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("manifest-case")
                .caseNumber("manifest-case")
                .fileName("sample.mp4")
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

        EvidenceManifest manifest = evidenceManifestRepository.findById(evidence.getEvidenceId()).orElseThrow();
        assertThat(manifest.getManifestHash()).hasSize(64);
        assertThat(manifest.getManifestStoragePath()).contains("/manifest/evidence-manifest.json");
        assertThat(manifest.getSignatureStatus()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(manifest.getSignatureValue()).isNotBlank();
        assertThat(evidenceManifestService.isSignatureValid(manifest)).isTrue();
        assertThat(manifest.getManifestJson()).contains("\"originalHash\"");
        assertThat(manifest.getManifestJson()).contains("\"originalSha256\"");
        assertThat(manifest.getManifestJson()).contains("\"fileId\"");
        assertThat(manifest.getManifestJson()).contains("\"caseId\"");
        assertThat(manifest.getManifestJson()).contains("\"uploadedAt\"");
        assertThat(manifest.getManifestJson()).contains("\"signer\"");
    }
}
