package com.example.demo.scheduler;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.AnalysisWorkerService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        "analysis.worker.stale-reaper-enabled=true",
        "analysis.worker.stale-timeout-minutes=1",
        "blockchain.anchor.scheduler-enabled=false"
})
@ActiveProfiles("test")
@TestPropertySource(properties = "analysis.worker.stale-reaper-interval-ms=60000")
class AnalysisStaleJobReaperTest {

    @Autowired
    private AnalysisStaleJobReaper analysisStaleJobReaper;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AnalysisRequest staleQueuedRequest;

    @BeforeEach
    void setUp() {
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .loginId("stale-user")
                .email("stale-user@test.local")
                .password(passwordEncoder.encode("pass1234"))
                .name("Stale User")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("Stale Test")
                .fileName("stale.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(10L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc123def4567890abc123def4567890abc123def4567890abc123def4567890")
                .originalStoragePath("s3://bucket/stale.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        staleQueuedRequest = new AnalysisRequest();
        staleQueuedRequest.setEvidenceId(evidence.getEvidenceId());
        staleQueuedRequest.setRequestedBy(user.getUserId());
        staleQueuedRequest.setStatus(AnalysisStatus.QUEUED);
        staleQueuedRequest.setProgressPercent(0);
        staleQueuedRequest.setRequestedAt(LocalDateTime.now().minusMinutes(5));
        staleQueuedRequest = analysisRequestRepository.save(staleQueuedRequest);
    }

    @AfterEach
    void tearDown() {
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void reapStaleJobs_marksOldQueuedRequestAsFailed() {
        analysisStaleJobReaper.reapStaleJobs();

        AnalysisRequest updated = analysisRequestRepository.findById(staleQueuedRequest.getAnalysisRequestId())
                .orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(updated.getErrorCode()).isEqualTo("ANALYSIS_TIMEOUT");
    }
}
