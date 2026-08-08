package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userToken;
    private User testUser;
    private String reviewerToken;
    private User reviewer;
    private Evidence evidence;

    @BeforeEach
    void setUp() throws Exception {
        reportRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        caseProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .loginId("1111")
                .email("1111@local.dev")
                .password(passwordEncoder.encode("2222"))
                .name("테스트 사용자")
                .organizationType(OrgType.ETC)
                .department("로컬개발팀")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        userToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");

        reviewer = userRepository.save(User.builder()
                .loginId("reviewer")
                .email("reviewer@local.dev")
                .password(passwordEncoder.encode("2222"))
                .name("검토자")
                .organizationType(OrgType.ETC)
                .department("로컬개발팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
        reviewerToken = JwtTestSupport.loginAndGetToken(mockMvc, "reviewer", "2222");

        evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("report-case")
                .caseNumber("report-case")
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(1024L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("b".repeat(64))
                .originalStoragePath("s3://bucket/report")
                .uploadedAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        reportRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        caseProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldListReports() throws Exception {
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
        result.setRiskScore(80.0);
        result.setConfidenceScore(0.9);
        result.setRiskLevel(RiskLevel.HIGH);
        result.setSummary("high risk");
        result.setAnalyzedAt(LocalDateTime.now());
        result = analysisResultRepository.save(result);

        Report report = new Report();
        report.setAnalysisResultId(result.getAnalysisResultId());
        report.setEvidenceId(evidence.getEvidenceId());
        report.setCreatedBy(testUser.getUserId());
        report.setReportFileName("analysis-report-" + evidence.getEvidenceId() + ".pdf");
        report.setStoragePath("reports/test.pdf");
        report.setReportHash("c".repeat(64));
        report.setFileSize(2048L);
        report.setCreatedAt(LocalDateTime.now());
        report.markIssued(testUser.getUserId(), LocalDateTime.now());
        reportRepository.save(report);

        mockMvc.perform(get("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].reportType").value("ANALYSIS"))
                .andExpect(jsonPath("$.content[0].caseName").value("report-case"))
                .andExpect(jsonPath("$.content[0].verdictLabel").value("위험"))
                .andExpect(jsonPath("$.content[0].publicationStatus").value("ISSUED"))
                .andExpect(jsonPath("$.content[0].version").value(1))
                .andExpect(jsonPath("$.content[0].downloadPath")
                        .value("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/pdf"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldListIssuedReportsForAssignedReviewer() throws Exception {
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                testUser.getUserId(),
                "report-case",
                evidence.getEvidenceId()
        ));
        profile.assignReviewer(reviewer.getUserId());
        profile.approveReview();
        caseProfileRepository.save(profile);

        Report report = new Report();
        report.setEvidenceId(evidence.getEvidenceId());
        report.setCreatedBy(testUser.getUserId());
        report.setReportFileName("analysis-report-" + evidence.getEvidenceId() + ".pdf");
        report.setStoragePath("reports/reviewer-test.pdf");
        report.setReportHash("d".repeat(64));
        report.setFileSize(2048L);
        report.setCreatedAt(LocalDateTime.now());
        report.markIssued(reviewer.getUserId(), LocalDateTime.now());
        reportRepository.save(report);

        mockMvc.perform(get("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].reportId").value(report.getReportId()))
                .andExpect(jsonPath("$.content[0].caseName").value("report-case"))
                .andExpect(jsonPath("$.content[0].publicationStatus").value("ISSUED"));
    }
}
