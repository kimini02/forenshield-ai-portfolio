package com.example.demo.support;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceManifestRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.AnalysisJobEnqueuer;
import com.example.demo.security.SignupRateLimitService;
import com.example.demo.service.readiness.EvidenceReadinessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class AbstractEvidenceIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected EvidenceRepository evidenceRepository;

    @Autowired
    protected AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    protected AnalysisResultRepository analysisResultRepository;

    @Autowired
    protected CustodyLogRepository custodyLogRepository;

    @Autowired
    protected EvidenceManifestRepository evidenceManifestRepository;

    @Autowired
    protected EvidenceMetadataRepository evidenceMetadataRepository;

    @Autowired
    protected EvidenceReadinessService evidenceReadinessService;

    @Autowired
    protected NotificationRepository notificationRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected SignupRateLimitService signupRateLimitService;

    @MockBean
    protected AnalysisJobEnqueuer analysisJobEnqueuer;

    @Autowired
    protected S3Client s3Client;

    @Value("${aws.s3.evidence-bucket}")
    protected String evidenceBucket;

    @Value("${file.upload-dir}")
    protected String uploadDir;

    protected String accessToken;
    protected String stepUpToken;

    @BeforeEach
    void setUpEvidenceIntegrationTest() throws Exception {
        signupRateLimitService.reset();
        notificationRepository.deleteAll();
        evidenceManifestRepository.deleteAll();
        custodyLogRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(EvidenceTestFixtures.defaultApprovedUser(passwordEncoder));
        accessToken = JwtTestSupport.loginAndGetToken(
                mockMvc,
                EvidenceTestFixtures.DEFAULT_LOGIN_ID,
                EvidenceTestFixtures.DEFAULT_PASSWORD
        );
        stepUpToken = StepUpTestSupport.issueStepUpToken(
                mockMvc,
                accessToken,
                EvidenceTestFixtures.DEFAULT_PASSWORD
        );
    }

    @AfterEach
    void tearDownEvidenceIntegrationTest() throws Exception {
        custodyLogRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
        Path root = Paths.get(uploadDir);
        if (Files.exists(root)) {
            FileSystemUtils.deleteRecursively(root);
        }
    }

    protected String bearerToken() {
        return EvidenceApiTestSupport.bearer(accessToken);
    }

    protected User currentUser() {
        return userRepository.findByLoginIdAndDeletedAtIsNull(EvidenceTestFixtures.DEFAULT_LOGIN_ID)
                .orElseThrow();
    }

    protected Evidence saveVideoEvidence(User user, String fileName) {
        return evidenceRepository.save(EvidenceTestFixtures.videoEvidence(user.getUserId(), fileName, 'b'));
    }

    protected AnalysisRequest saveAnalysisRequest(
            Evidence evidence,
            User user,
            AnalysisStatus status
    ) {
        return analysisRequestRepository.save(
                EvidenceTestFixtures.analysisRequest(evidence, user, status)
        );
    }

    protected void saveAnalysisResult(AnalysisRequest request, double riskScore, RiskLevel riskLevel) {
        analysisResultRepository.save(EvidenceTestFixtures.analysisResult(request, riskScore, riskLevel));
    }

    protected void seedS3Object(String objectKey, byte[] content) {
        EvidenceApiTestSupport.seedS3Object(s3Client, evidenceBucket, objectKey, content);
    }

    protected long uploadAndStartAnalysis(String fileName, String caseName) throws Exception {
        return EvidenceApiTestSupport.uploadAndStartAnalysis(
                mockMvc, objectMapper, accessToken, fileName, caseName
        );
    }
}
