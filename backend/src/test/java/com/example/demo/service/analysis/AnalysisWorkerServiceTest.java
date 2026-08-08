package com.example.demo.service.analysis;

import com.example.demo.service.evidence.EvidenceCopyService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CustodyLogRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class AnalysisWorkerServiceTest {

    @Autowired
    private AnalysisWorkerService analysisWorkerService;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private EvidenceCopyService evidenceCopyService;

    @BeforeEach
    void setUp() {
        custodyLogRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("분석 워커 완료 시 ANALYSIS_STARTED/COMPLETED 및 사본 삭제 CoC를 기록한다")
    void processJob_completed_recordsAnalysisCustodyLogs() throws Exception {
        User user = userRepository.save(User.builder()
                .loginId("worker-user")
                .email("worker@test.dev")
                .password("encoded")
                .name("Worker User")
                .organizationType(OrgType.ETC)
                .department("test")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        byte[] fileBytes = "worker coc video bytes".getBytes(StandardCharsets.UTF_8);
        Path tempFile = Files.createTempFile("worker-coc", ".mp4");
        Files.write(tempFile, fileBytes);
        String originalKey = "cases/test-case/1/original/worker-coc.mp4";
        s3Client.putObject(
                PutObjectRequest.builder().bucket("test-evidence-bucket").key(originalKey).build(),
                RequestBody.fromBytes(fileBytes)
        );

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("test-case")
                .caseNumber("test-case")
                .fileName("worker-coc.mp4")
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

        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(user.getUserId());
        request.setStatus(AnalysisStatus.QUEUED);
        request.setProgressPercent(0);
        request.setRequestedAt(LocalDateTime.now());
        AnalysisRequest savedRequest = analysisRequestRepository.save(request);

        analysisWorkerService.processJob(savedRequest.getAnalysisRequestId());

        AnalysisRequest completed = analysisRequestRepository.findById(savedRequest.getAnalysisRequestId())
                .orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);

        Evidence evidenceAfter = evidenceRepository.findById(evidence.getEvidenceId()).orElseThrow();
        assertThat(evidenceAfter.getCopyStatus()).isEqualTo(CopyStatus.DELETED);

        List<CustodyLog> requestLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_REQUEST,
                        savedRequest.getAnalysisRequestId()
                );
        assertThat(requestLogs)
                .extracting(CustodyLog::getActionType)
                .contains("ANALYSIS_STARTED");

        List<CustodyLog> resultLogs = custodyLogRepository.findAll().stream()
                .filter(log -> log.getActionType().equals("ANALYSIS_COMPLETED"))
                .toList();
        assertThat(resultLogs).hasSize(1);
        assertThat(resultLogs.get(0).getTargetType()).isEqualTo(CustodyTargetType.ANALYSIS_RESULT);

        List<CustodyLog> evidenceLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidence.getEvidenceId());
        assertThat(evidenceLogs)
                .extracting(CustodyLog::getActionType)
                .contains("ANALYSIS_COPY_DELETED");

        Files.deleteIfExists(tempFile);
    }
}
