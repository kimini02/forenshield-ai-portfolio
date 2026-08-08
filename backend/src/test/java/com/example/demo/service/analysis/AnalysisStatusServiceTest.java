package com.example.demo.service.analysis;

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
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class AnalysisStatusServiceTest {

    @Autowired
    private AnalysisStatusService analysisStatusService;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;
    private Evidence evidence;

    @BeforeEach
    void setUp() {
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .loginId("queue-user")
                .email("queue-user@test.local")
                .password(passwordEncoder.encode("pass1234"))
                .name("Queue User")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("Queue Test")
                .fileName("queue.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(10L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc123def4567890abc123def4567890abc123def4567890abc123def4567890")
                .originalStoragePath("s3://bucket/queue.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getStatus_exposesQueuePositionForQueuedRequest() {
        LocalDateTime base = LocalDateTime.now().minusMinutes(10);

        AnalysisRequest first = queuedRequest(evidence.getEvidenceId(), base);
        analysisRequestRepository.save(first);

        Evidence secondEvidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("Queue Test 2")
                .fileName("queue2.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(10L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("def456abc7890123def456abc7890123def456abc7890123def456abc7890123")
                .originalStoragePath("s3://bucket/queue2.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        AnalysisRequest second = queuedRequest(secondEvidence.getEvidenceId(), base.plusMinutes(5));
        analysisRequestRepository.save(second);

        var response = analysisStatusService.getStatus(user, secondEvidence.getEvidenceId());

        assertThat(response.getQueueStatus()).isEqualTo("WAITING");
        assertThat(response.getQueuePosition()).isEqualTo(2);
        assertThat(response.getQueueDepth()).isEqualTo(2);
    }

    private AnalysisRequest queuedRequest(Long evidenceId, LocalDateTime requestedAt) {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidenceId);
        request.setRequestedBy(user.getUserId());
        request.setStatus(AnalysisStatus.QUEUED);
        request.setProgressPercent(0);
        request.setRequestedAt(requestedAt);
        return request;
    }
}
