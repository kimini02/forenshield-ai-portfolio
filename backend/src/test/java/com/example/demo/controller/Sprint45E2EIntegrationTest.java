package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.AnalysisWorkerService;
import com.example.demo.support.JwtTestSupport;
import com.example.demo.support.StepUpTestSupport;
import com.example.demo.security.SignupRateLimitService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class Sprint45E2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private AnalysisModuleResultRepository analysisModuleResultRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private AnalysisWorkerService analysisWorkerService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SignupRateLimitService signupRateLimitService;

    private String userToken;
    private String stepUpToken;
    private User testUser;
    private Evidence evidence;

    @BeforeEach
    void setUp() throws Exception {
        signupRateLimitService.reset();
        analysisModuleResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        custodyLogRepository.deleteAll();
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .loginId("sprint45")
                .email("sprint45@test.local")
                .password(passwordEncoder.encode("pass1234"))
                .name("Sprint45 User")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("Sprint45 E2E")
                .fileName("e2e-sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(1024L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc123def4567890abc123def4567890abc123def4567890abc123def4567890")
                .originalStoragePath("s3://bucket/e2e-sample.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        userToken = JwtTestSupport.loginAndGetToken(mockMvc, "sprint45", "pass1234");
        stepUpToken = StepUpTestSupport.issueStepUpToken(mockMvc, userToken, "pass1234");
    }

    @AfterEach
    void tearDown() {
        analysisModuleResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        custodyLogRepository.deleteAll();
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void aiResultToDetail_e2ePipeline() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(testUser.getUserId());
        request.setStatus(AnalysisStatus.QUEUED);
        request.setProgressPercent(0);
        request.setRequestedAt(LocalDateTime.now());
        request = analysisRequestRepository.save(request);

        analysisWorkerService.applyAiResult(AnalysisResponseMessage.builder()
                .analysisRequestId(request.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .status("COMPLETED")
                .riskScore(81.0)
                .confidenceScore(0.93)
                .riskLevel("HIGH")
                .analysisReasons(List.of("Lip-sync anomaly detected."))
                .analyzedAt("2026-06-19T05:00:00Z")
                .results(List.of(AnalysisResponseMessage.AnalysisVideoResultItem.builder()
                        .type("video")
                        .deepfakeDetected(true)
                        .deepfakeScore(0.89)
                        .lipSyncDetected(true)
                        .lipSyncScore(0.76)
                        .frameEditDetected(false)
                        .build()))
                .build());

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/detail")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisInfo.status").value("COMPLETED"))
                .andExpect(jsonPath("$.analysisInfo.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.analysisInfo.modelScores").isArray())
                .andExpect(jsonPath("$.analysisInfo.moduleResults").isArray());

        assertThat(analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId())).isPresent();
    }

    @Test
    void integrityAndCocVerify_endpoints() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/integrity/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(evidence.getEvidenceId()))
                .andExpect(jsonPath("$.valid").isBoolean())
                .andExpect(jsonPath("$.checks").isArray());

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/coc/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").isBoolean());
    }

    @Test
    void adminApi_rejectsRegularUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void reportPdf_recordsReportCustodyLogs() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(testUser.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now());
        request.setCompletedAt(LocalDateTime.now());
        request = analysisRequestRepository.save(request);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(55.0);
        result.setConfidenceScore(0.85);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary("E2E report test");
        result.setAnalyzedAt(LocalDateTime.now());
        analysisResultRepository.save(result);

        CaseProfile profile = new CaseProfile(
                testUser.getUserId(),
                "Sprint45 E2E",
                evidence.getEvidenceId()
        );
        profile.approveReview();
        caseProfileRepository.save(profile);

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE));

        List<CustodyLog> reportLogs = custodyLogRepository.findAll().stream()
                .filter(log -> "REPORT_CREATED".equals(log.getActionType())
                        || "REPORT_DOWNLOADED".equals(log.getActionType()))
                .toList();

        assertThat(reportLogs.stream().map(CustodyLog::getActionType))
                .contains("REPORT_CREATED", "REPORT_DOWNLOADED");
    }
}
