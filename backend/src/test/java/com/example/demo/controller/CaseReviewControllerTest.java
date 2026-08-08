package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.ReportPublicationStatus;
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
        custodyLogRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        reportRepository.deleteAll();
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
        custodyLogRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        reportRepository.deleteAll();
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
                .andExpect(jsonPath("$.reviewerComment").value("최종 보고서 발행 승인"));

        var issuedReport = reportRepository
                .findTopByEvidenceIdOrderByCreatedAtDesc(completedEvidence.getEvidenceId())
                .orElseThrow();
        assertThat(issuedReport.getPublicationStatus()).isEqualTo(ReportPublicationStatus.ISSUED);
        assertThat(issuedReport.getVerificationToken()).isNotBlank();
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
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_SUPPLEMENT_REQUESTED"));
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
