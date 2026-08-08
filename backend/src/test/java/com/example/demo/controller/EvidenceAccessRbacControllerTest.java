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
import com.example.demo.support.StepUpTestSupport;
import com.example.demo.security.SignupRateLimitService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class EvidenceAccessRbacControllerTest {

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

    @Autowired
    private SignupRateLimitService signupRateLimitService;

    private User investigator;
    private User reviewer;
    private User orgAdmin;
    private User otherInvestigator;
    private String investigatorToken;
    private String reviewerToken;
    private String orgAdminToken;
    private String investigatorStepUpToken;
    private String reviewerStepUpToken;
    private String orgAdminStepUpToken;
    private Evidence assignedEvidence;
    private Evidence hiddenEvidence;

    @BeforeEach
    void setUp() throws Exception {
        signupRateLimitService.reset();
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        investigator = userRepository.save(User.builder()
                .loginId("inv10")
                .email("inv10@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("분석관")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        reviewer = userRepository.save(User.builder()
                .loginId("rev10")
                .email("rev10@local.dev")
                .password(passwordEncoder.encode("pass2222"))
                .name("검토자")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        orgAdmin = userRepository.save(User.builder()
                .loginId("adm10")
                .email("adm10@local.dev")
                .password(passwordEncoder.encode("pass3333"))
                .name("기관관리자")
                .organizationType(OrgType.POLICE)
                .department("관리자실")
                .role(UserRole.ROLE_ORG_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        otherInvestigator = userRepository.save(User.builder()
                .loginId("inv11")
                .email("inv11@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("다른분석관")
                .organizationType(OrgType.POLICE)
                .department("수사1팀")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        investigatorToken = JwtTestSupport.loginAndGetToken(mockMvc, "inv10", "pass1111");
        reviewerToken = JwtTestSupport.loginAndGetToken(mockMvc, "rev10", "pass2222");
        orgAdminToken = JwtTestSupport.loginAndGetToken(mockMvc, "adm10", "pass3333");
        investigatorStepUpToken = StepUpTestSupport.issueStepUpToken(mockMvc, investigatorToken, "pass1111");
        reviewerStepUpToken = StepUpTestSupport.issueStepUpToken(mockMvc, reviewerToken, "pass2222");
        orgAdminStepUpToken = StepUpTestSupport.issueStepUpToken(mockMvc, orgAdminToken, "pass3333");

        assignedEvidence = saveEvidence(investigator, "assigned-access-case", "assigned.mp4");
        hiddenEvidence = saveEvidence(otherInvestigator, "hidden-access-case", "hidden.mp4");

        CaseProfile profile = caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "assigned-access-case",
                assignedEvidence.getEvidenceId()
        ));
        profile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(profile);
    }

    @AfterEach
    void tearDown() {
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void reviewerCanReadAssignedEvidenceDetail() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", assignedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, reviewerStepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceInfo.evidenceId").value(assignedEvidence.getEvidenceId()));
    }

    @Test
    void reviewerCannotReadUnassignedEvidenceDetail() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", hiddenEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, reviewerStepUpToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void orgAdminCanReadOrganizationEvidenceDetail() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", assignedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, orgAdminStepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceInfo.evidenceId").value(assignedEvidence.getEvidenceId()));
    }

    @Test
    void reviewerCanReadAssignedEvidenceAnalysisStatus() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/analysis-status", assignedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(assignedEvidence.getEvidenceId()));
    }

    @Test
    void reviewerCannotMutateAssignedEvidence() throws Exception {
        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}", assignedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evidenceIds":[%d]}
                                """.formatted(assignedEvidence.getEvidenceId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void investigatorStillReadsOwnEvidenceDetail() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", assignedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investigatorToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, investigatorStepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceInfo.evidenceId").value(assignedEvidence.getEvidenceId()));
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
                .originalHashValue("e".repeat(64))
                .originalStoragePath("uploads/test/" + fileName)
                .uploadedAt(LocalDateTime.now())
                .build());
    }
}
