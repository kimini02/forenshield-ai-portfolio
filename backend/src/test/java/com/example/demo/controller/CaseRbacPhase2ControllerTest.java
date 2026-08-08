package com.example.demo.controller;

import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class CaseRbacPhase2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User investigator;
    private User reviewer;
    private User otherDepartmentReviewer;
    private User orgAdmin;
    private String investigatorToken;
    private String reviewerToken;
    private String orgAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        caseProfileRepository.deleteAll();
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
    }

    @AfterEach
    void tearDown() {
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getCaseDetail_includesRbacFields() throws Exception {
        saveEvidence(investigator, "rbac-detail-case", "detail.mp4");

        mockMvc.perform(get("/api/v1/cases?caseKey=rbac-detail-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investigatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("rbac-detail-case"))
                .andExpect(jsonPath("$.organizationId").value("org-police"))
                .andExpect(jsonPath("$.createdBy").value(String.valueOf(investigator.getUserId())))
                .andExpect(jsonPath("$.assigneeId").value(String.valueOf(investigator.getUserId())))
                .andExpect(jsonPath("$.reviewStatus").value("NONE"));
    }

    @Test
    void assignReviewer_setsReviewerAndReturnsCaseDetail() throws Exception {
        Evidence evidence = saveEvidence(investigator, "assign-case", "assign.mp4");
        caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "assign-case",
                evidence.getEvidenceId()
        ));

        mockMvc.perform(patch("/api/v1/cases/{caseId}/reviewer", "assign-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": "%d",
                                  "uploaderId": "%d"
                                }
                                """.formatted(reviewer.getUserId(), investigator.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewerId").value(String.valueOf(reviewer.getUserId())))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_ASSIGNED"))
                .andExpect(jsonPath("$.createdBy").value(String.valueOf(investigator.getUserId())));
    }

    @Test
    void assignReviewer_rejectsReviewerFromDifferentDepartment() throws Exception {
        Evidence evidence = saveEvidence(investigator, "different-department-case", "different-department.mp4");
        caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "different-department-case",
                evidence.getEvidenceId()
        ));

        mockMvc.perform(patch("/api/v1/cases/{caseId}/reviewer", "different-department-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": "%d",
                                  "uploaderId": "%d"
                                }
                                """.formatted(otherDepartmentReviewer.getUserId(), investigator.getUserId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REVIEWER_SCOPE"));
    }

    @Test
    void reviewerCanOpenAssignedCaseDetail() throws Exception {
        Evidence evidence = saveEvidence(investigator, "reviewer-detail-case", "review.mp4");
        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "reviewer-detail-case",
                evidence.getEvidenceId()
        ));
        profile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(profile);

        mockMvc.perform(get("/api/v1/cases?caseKey=reviewer-detail-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("reviewer-detail-case"))
                .andExpect(jsonPath("$.reviewerId").value(String.valueOf(reviewer.getUserId())))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_ASSIGNED"));
    }

    @Test
    void listApprovedReviewersForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "ROLE_REVIEWER")
                        .param("status", "APPROVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(String.valueOf(reviewer.getUserId()))));
    }

    private Evidence saveEvidence(User uploader, String caseName, String fileName) {
        return evidenceRepository.save(Evidence.builder()
                .uploaderId(uploader.getUserId())
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
