package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.report.ReportPdfService;
import com.example.demo.support.JwtTestSupport;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class CaseReviewControllerTest {

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

    @SpyBean
    private ReportIssueTaskRepository reportIssueTaskRepository;

    @SpyBean
    private ReportPdfService reportPdfService;

    @SpyBean
    private BlockchainAnchorService blockchainAnchorService;

    @Autowired
    private BlockchainAnchorRepository blockchainAnchorRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User investigator;
    private User reviewer;
    private User otherDepartmentReviewer;
    private User orgAdmin;
    private String investigatorToken;
    private String reviewerToken;
    private String orgAdminToken;
    private Evidence completedEvidence;

    @BeforeEach
    void setUp() throws Exception {
        reset(reportIssueTaskRepository, reportPdfService, blockchainAnchorService);
        custodyLogRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        reportRepository.deleteAll();
        reportIssueTaskRepository.deleteAll();
        caseProfileRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        investigator = userRepository.save(User.builder()
                .loginId("inv02")
                .email("inv02@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("분석관")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        reviewer = userRepository.save(User.builder()
                .loginId("rev02")
                .email("rev02@local.dev")
                .password(passwordEncoder.encode("pass2222"))
                .name("검토자")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        otherDepartmentReviewer = userRepository.save(User.builder()
                .loginId("rev-other-dept")
                .email("rev-other-dept@local.dev")
                .password(passwordEncoder.encode("pass2222"))
                .name("다른부서검토자")
                .organizationType(OrgType.POLICE)
                .department("디지털포렌식팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        orgAdmin = userRepository.save(User.builder()
                .loginId("adm02")
                .email("adm02@local.dev")
                .password(passwordEncoder.encode("pass3333"))
                .name("기관관리자")
                .organizationType(OrgType.POLICE)
                .department("관리자실")
                .role(UserRole.ROLE_ORG_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        investigatorToken = JwtTestSupport.loginAndGetToken(mockMvc, "inv02", "pass1111");
        reviewerToken = JwtTestSupport.loginAndGetToken(mockMvc, "rev02", "pass2222");
        orgAdminToken = JwtTestSupport.loginAndGetToken(mockMvc, "adm02", "pass3333");

        completedEvidence = saveCompletedEvidence("review-case", "completed.mp4");
    }

    @AfterEach
    void tearDown() {
        reset(reportIssueTaskRepository, reportPdfService, blockchainAnchorService);
        custodyLogRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        reportRepository.deleteAll();
        reportIssueTaskRepository.deleteAll();
        caseProfileRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void reviewWorkflow_requestAssignAndApprove() throws Exception {
        AnalysisRequest completedRequest = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(completedEvidence.getEvidenceId())
                .orElseThrow();
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(completedRequest.getAnalysisRequestId());
        result.setRiskScore(64.0);
        result.setConfidenceScore(0.88);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary("review lifecycle test");
        result.setAnalyzedAt(LocalDateTime.now());
        analysisResultRepository.save(result);

        mockMvc.perform(post("/api/v1/cases/review-request?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investigatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memo":"검토 부탁드립니다"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_REQUESTED"))
                .andExpect(jsonPath("$.createdBy").value(String.valueOf(investigator.getUserId())));

        mockMvc.perform(patch("/api/v1/cases/reviewer?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId": "%s"}
                                """.formatted(reviewer.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_ASSIGNED"))
                .andExpect(jsonPath("$.reviewerId").value(String.valueOf(reviewer.getUserId())));

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","memo":"최종 보고서 발행 승인"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REPORT_APPROVED"))
                .andExpect(jsonPath("$.reportIssueStatus").value("PENDING"))
                .andExpect(jsonPath("$.reviewerComment").value("최종 보고서 발행 승인"));

        assertThat(reportRepository.count()).isZero();
        assertThat(blockchainAnchorRepository.count()).isZero();
        verify(reportPdfService, never()).issueCaseReports(org.mockito.ArgumentMatchers.any(), anyList());
        verify(blockchainAnchorService, never()).anchorReportHash(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(reportIssueTaskRepository.findAll())
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getEvidenceId()).isEqualTo(completedEvidence.getEvidenceId());
                    assertThat(task.getAnalysisResultId()).isEqualTo(result.getAnalysisResultId());
                    assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.PENDING);
                    assertThat(task.getAttemptCount()).isZero();
                });
    }

    @Test
    void reviewWorkflow_reviewerCanRequestRevision() throws Exception {
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "review-case",
                completedEvidence.getEvidenceId()
        ));
        profile.requestReview("memo");
        profile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(profile);

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REVISION"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_SUPPLEMENT_REQUESTED"))
                .andExpect(jsonPath("$.reportIssueStatus").value("NOT_REQUIRED"));

        assertThat(reportIssueTaskRepository.count()).isZero();
    }

    @Test
    void reviewWorkflow_threeEligibleEvidencesCreateThreePendingTasks() throws Exception {
        saveAnalysisResultForLatest(completedEvidence, "first");
        Evidence second = saveCompletedEvidence("review-case", "second.mp4");
        Evidence third = saveCompletedEvidence("review-case", "third.mp4");
        saveAnalysisResultForLatest(second, "second");
        saveAnalysisResultForLatest(third, "third");
        CaseProfile profile = assignedProfile();

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportIssueStatus").value("PENDING"));

        assertThat(caseProfileRepository.findById(profile.getCaseProfileId()).orElseThrow().getReviewStatus())
                .isEqualTo(CaseReviewStatus.REPORT_APPROVED);
        assertThat(reportIssueTaskRepository.findAll()).hasSize(3)
                .allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.PENDING));
        assertThat(reportRepository.count()).isZero();
        assertThat(blockchainAnchorRepository.count()).isZero();
    }

    @Test
    void reviewWorkflow_taskInsertFailureRollsBackApproval() throws Exception {
        saveAnalysisResultForLatest(completedEvidence, "rollback");
        CaseProfile profile = assignedProfile();
        doThrow(new DataIntegrityViolationException("forced task insert failure"))
                .when(reportIssueTaskRepository).saveAllAndFlush(anyList());

        int responseStatus;
        try {
            responseStatus = mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"decision":"APPROVED"}
                                    """))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception expected) {
            responseStatus = 500;
        }

        reset(reportIssueTaskRepository);
        assertThat(responseStatus).isGreaterThanOrEqualTo(500);
        assertThat(caseProfileRepository.findById(profile.getCaseProfileId()).orElseThrow().getReviewStatus())
                .isEqualTo(CaseReviewStatus.REVIEW_ASSIGNED);
        assertThat(reportIssueTaskRepository.count()).isZero();
    }

    @Test
    void reviewWorkflow_latestRequestNotCompletedCreatesNoTask() throws Exception {
        AnalysisRequest completed = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(completedEvidence.getEvidenceId())
                .orElseThrow();
        completed.setRequestedAt(LocalDateTime.now().minusMinutes(1));
        analysisRequestRepository.save(completed);
        saveAnalysisResultForLatest(completedEvidence, "older completed result");

        AnalysisRequest latest = new AnalysisRequest();
        latest.setEvidenceId(completedEvidence.getEvidenceId());
        latest.setRequestedBy(investigator.getUserId());
        latest.setStatus(AnalysisStatus.ANALYZING);
        latest.setProgressPercent(50);
        latest.setRequestedAt(LocalDateTime.now());
        analysisRequestRepository.save(latest);
        assignedProfile();

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportIssueStatus").value("NOT_REQUIRED"));

        assertThat(reportIssueTaskRepository.count()).isZero();
    }

    @Test
    void reviewWorkflow_completedRequestWithoutResultCreatesNoTask() throws Exception {
        assignedProfile();

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportIssueStatus").value("NOT_REQUIRED"));

        assertThat(reportIssueTaskRepository.count()).isZero();
    }

    @Test
    void reviewWorkflow_alreadyIssuedReportCreatesNoTask() throws Exception {
        AnalysisResult result = saveAnalysisResultForLatest(completedEvidence, "already issued");
        Report report = new Report();
        report.setAnalysisResultId(result.getAnalysisResultId());
        report.setEvidenceId(completedEvidence.getEvidenceId());
        report.setCreatedBy(investigator.getUserId());
        report.setReportFileName("already-issued.pdf");
        report.setStoragePath("isolated-test/already-issued.pdf");
        report.setReportHash("a".repeat(64));
        report.setFileSize(100L);
        report.setCreatedAt(LocalDateTime.now());
        report.markIssued(reviewer.getUserId(), LocalDateTime.now());
        reportRepository.save(report);
        assignedProfile();

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportIssueStatus").value("NOT_REQUIRED"));

        assertThat(reportIssueTaskRepository.count()).isZero();
        assertThat(reportRepository.count()).isOne();
    }

    @Test
    void reportDownload_usesCaseApprovalForEvidenceCompletedAfterThePreviousApproval() throws Exception {
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "review-case",
                completedEvidence.getEvidenceId()
        ));
        profile.assignReviewer(reviewer.getUserId());
        profile.approveReview();
        caseProfileRepository.save(profile);

        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(completedEvidence.getEvidenceId())
                .orElseThrow();
        request.setCompletedAt(profile.getReviewApprovedAt().plusSeconds(1));
        analysisRequestRepository.save(request);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(52.0);
        result.setConfidenceScore(0.81);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary("case-level approval report");
        result.setAnalyzedAt(request.getCompletedAt());
        analysisResultRepository.save(result);

        byte[] pdfBytes = mockMvc.perform(get("/api/v1/evidences/" + completedEvidence.getEvidenceId() + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        PdfReader pdfReader = new PdfReader(pdfBytes);
        try {
            StringBuilder reportText = new StringBuilder();
            for (int page = 1; page <= pdfReader.getNumberOfPages(); page++) {
                reportText.append(new PdfTextExtractor(pdfReader).getTextFromPage(page));
            }
            assertThat(reportText.toString()).contains("review-case", "분석관", "검토자");
        } finally {
            pdfReader.close();
        }
    }

    @Test
    void reviewWorkflow_assignedReviewerCanApproveAgainAfterNewEvidenceIsAdded() throws Exception {
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "review-case",
                completedEvidence.getEvidenceId()
        ));
        profile.assignReviewer(reviewer.getUserId());
        profile.approveReview();
        caseProfileRepository.save(profile);

        profile.reopenReviewForNewEvidence();
        caseProfileRepository.save(profile);

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REPORT_APPROVED"))
                .andExpect(jsonPath("$.reviewerId").value(String.valueOf(reviewer.getUserId())));
    }

    @Test
    void reviewWorkflow_assignedReviewerCanApproveAnAlreadyApprovedCase() throws Exception {
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "review-case",
                completedEvidence.getEvidenceId()
        ));
        profile.assignReviewer(reviewer.getUserId());
        profile.approveReview();
        caseProfileRepository.save(profile);

        mockMvc.perform(post("/api/v1/cases/review-decision?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REPORT_APPROVED"));
    }

    @Test
    void assignReviewerByCaseKey_rejectsReviewerFromDifferentDepartment() throws Exception {
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "review-case",
                completedEvidence.getEvidenceId()
        ));
        profile.requestReview("memo");
        caseProfileRepository.save(profile);

        mockMvc.perform(patch("/api/v1/cases/reviewer?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId": "%s"}
                                """.formatted(otherDepartmentReviewer.getUserId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REVIEWER_SCOPE"));
    }

    @Test
    void getCaseDetail_reviewerCanAccessAssignedCase() throws Exception {
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "review-case",
                completedEvidence.getEvidenceId()
        ));
        profile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(profile);

        mockMvc.perform(get("/api/v1/cases?caseKey=review-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("review-case"))
                .andExpect(jsonPath("$.reviewerId").value(String.valueOf(reviewer.getUserId())))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_ASSIGNED"));
    }

    @Test
    void requestReview_rejectsIncompleteCase() throws Exception {
        Evidence pendingEvidence = saveEvidence("pending-case", "pending.mp4");

        mockMvc.perform(post("/api/v1/cases/review-request?caseKey=pending-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investigatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        analysisRequestRepository.deleteAll();
        evidenceRepository.delete(pendingEvidence);
    }

    private Evidence saveCompletedEvidence(String caseName, String fileName) {
        Evidence evidence = saveEvidence(caseName, fileName);
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(investigator.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now());
        analysisRequestRepository.save(request);
        return evidence;
    }

    private AnalysisResult saveAnalysisResultForLatest(Evidence evidence, String summary) {
        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                .orElseThrow();
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(64.0);
        result.setConfidenceScore(0.88);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary(summary);
        result.setAnalyzedAt(LocalDateTime.now());
        return analysisResultRepository.save(result);
    }

    private CaseProfile assignedProfile() {
        CaseProfile profile = new CaseProfile(
                investigator.getUserId(),
                "review-case",
                completedEvidence.getEvidenceId()
        );
        profile.assignReviewer(reviewer.getUserId());
        return caseProfileRepository.save(profile);
    }

    private Evidence saveEvidence(String caseName, String fileName) {
        return evidenceRepository.save(Evidence.builder()
                .uploaderId(investigator.getUserId())
                .caseName(caseName)
                .caseNumber(caseName)
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm("SHA-256")
                .originalHashValue("d".repeat(64))
                .originalStoragePath("uploads/test/" + fileName)
                .uploadedAt(LocalDateTime.now())
                .build());
    }
}
