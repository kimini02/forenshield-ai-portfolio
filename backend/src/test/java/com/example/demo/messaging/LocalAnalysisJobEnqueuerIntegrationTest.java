package com.example.demo.messaging;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.StartAnalysisRequest;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.AnalysisService;
import com.example.demo.service.evidence.EvidenceCopyService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.domain.enums.FileType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class LocalAnalysisJobEnqueuerIntegrationTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceCopyService evidenceCopyService;

    @Autowired
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("startAnalysis 트랜잭션 커밋 후 로컬 워커가 분석을 완료한다")
    void startAnalysis_enqueuesAfterCommitAndCompletesJob() throws Exception {
        User user = userRepository.save(User.builder()
                .loginId("enqueue-user")
                .email("enqueue@test.dev")
                .password("encoded")
                .name("Enqueue User")
                .organizationType(OrgType.ETC)
                .department("test")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        byte[] fileBytes = "enqueue integration video bytes".getBytes(StandardCharsets.UTF_8);
        Path tempFile = Files.createTempFile("enqueue-integration", ".mp4");
        Files.write(tempFile, fileBytes);
        String originalKey = "cases/test-case/1/original/enqueue-integration.mp4";
        s3Client.putObject(
                PutObjectRequest.builder().bucket("test-evidence-bucket").key(originalKey).build(),
                RequestBody.fromBytes(fileBytes)
        );

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("test-case")
                .caseNumber("test-case")
                .fileName("enqueue-integration.mp4")
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

        StartAnalysisRequest request = new StartAnalysisRequest();
        request.setCaseName("test-case");
        request.setEvidenceIds(java.util.List.of(evidence.getEvidenceId()));

        analysisService.startAnalysis(user, request);

        AnalysisRequest queued = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                .orElseThrow();

        AnalysisRequest completed = waitForCompletion(queued.getAnalysisRequestId(), 5_000);
        assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.getProgressPercent()).isEqualTo(100);
    }

    private AnalysisRequest waitForCompletion(Long analysisRequestId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            AnalysisRequest current = analysisRequestRepository.findById(analysisRequestId).orElseThrow();
            if (current.getStatus() == AnalysisStatus.COMPLETED) {
                return current;
            }
            Thread.sleep(50);
        }
        AnalysisRequest last = analysisRequestRepository.findById(analysisRequestId).orElseThrow();
        throw new AssertionError("Expected COMPLETED but was " + last.getStatus());
    }
}
