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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class MyPageRbacControllerTest {

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
                .loginId("inv01")
                .email("inv01@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("분석관")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        reviewer = userRepository.save(User.builder()
                .loginId("rev01")
                .email("rev01@local.dev")
                .password(passwordEncoder.encode("pass2222"))
                .name("검토자")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        orgAdmin = userRepository.save(User.builder()
                .loginId("adm01")
                .email("adm01@local.dev")
                .password(passwordEncoder.encode("pass3333"))
                .name("기관관리자")
                .organizationType(OrgType.POLICE)
                .department("관리자실")
                .role(UserRole.ROLE_ORG_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        investigatorToken = JwtTestSupport.loginAndGetToken(mockMvc, "inv01", "pass1111");
        reviewerToken = JwtTestSupport.loginAndGetToken(mockMvc, "rev01", "pass2222");
        orgAdminToken = JwtTestSupport.loginAndGetToken(mockMvc, "adm01", "pass3333");
    }

    @AfterEach
    void tearDown() {
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getAnalysisHistory_includesRbacFieldsForUploadOnlyCase() throws Exception {
        Evidence evidence = saveEvidence(investigator, "rbac-case-01", "upload-only.mp4");

        mockMvc.perform(get("/api/v1/mypage/analysis-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investigatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].caseId").value("rbac-case-01"))
                .andExpect(jsonPath("$.content[0].organizationId").value("org-police"))
                .andExpect(jsonPath("$.content[0].createdBy").value(String.valueOf(investigator.getUserId())))
                .andExpect(jsonPath("$.content[0].assigneeId").value(String.valueOf(investigator.getUserId())))
                .andExpect(jsonPath("$.content[0].reviewStatus").value("NONE"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].representativeEvidenceId").value(evidence.getEvidenceId()));
    }

    @Test
    void getAnalysisHistory_includesProfileOnlyEmptyCase() throws Exception {
        caseProfileRepository.save(new CaseProfile(investigator.getUserId(), "empty-rbac-case", null));

        mockMvc.perform(get("/api/v1/mypage/analysis-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investigatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].caseId").value("empty-rbac-case"))
                .andExpect(jsonPath("$.content[0].evidenceCount").value(0))
                .andExpect(jsonPath("$.content[0].reviewStatus").value("NONE"))
                .andExpect(jsonPath("$.content[0].createdBy").value(String.valueOf(investigator.getUserId())));
    }

    @Test
    void getAnalysisHistory_reviewerSeesOnlyAssignedCases() throws Exception {
        saveEvidence(investigator, "unassigned-case", "hidden.mp4");
        Evidence assigned = saveEvidence(investigator, "assigned-case", "visible.mp4");
        caseProfileRepository.save(new CaseProfile(
                investigator.getUserId(),
                "assigned-case",
                assigned.getEvidenceId()
        ));
        CaseProfile profile = caseProfileRepository.findByUploaderIdAndCaseKey(
                investigator.getUserId(),
                "assigned-case"
        ).orElseThrow();
        profile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(profile);

        mockMvc.perform(get("/api/v1/mypage/analysis-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].caseId").value("assigned-case"))
                .andExpect(jsonPath("$.content[0].reviewerId").value(String.valueOf(reviewer.getUserId())));
    }

    @Test
    void getAnalysisHistory_orgAdminSeesOrganizationCases() throws Exception {
        saveEvidence(investigator, "org-case", "org.mp4");

        mockMvc.perform(get("/api/v1/mypage/analysis-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].caseId").value("org-case"));
    }

    @Test
    void login_mapsLegacyUserRoleToInvestigator() throws Exception {
        userRepository.save(User.builder()
                .loginId("legacy01")
                .email("legacy01@local.dev")
                .password(passwordEncoder.encode("pass4444"))
                .name("레거시")
                .organizationType(OrgType.ETC)
                .department("로컬개발팀")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"legacy01","password":"pass4444"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_INVESTIGATOR"));
    }

    @Test
    void approveAdminUser_withRole_assignsInvestigatorRole() throws Exception {
        User pending = userRepository.save(User.builder()
                .loginId("pending01")
                .email("pending01@local.dev")
                .password(passwordEncoder.encode("pass0000"))
                .name("대기사용자")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.PENDING)
                .darkMode(false)
                .build());

        mockMvc.perform(post("/api/v1/admin/users/{userId}/approve", pending.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "role": "REVIEWER" }
                                """))
                .andExpect(status().isOk());

        User approved = userRepository.findById(pending.getUserId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.ROLE_REVIEWER, approved.getRole());
    }

    @Test
    void updateAdminUser_withRole_changesRole() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{userId}", investigator.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "분석관",
                                  "email": "inv01@local.dev",
                                  "department": "사이버수사팀",
                                  "role": "REVIEWER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REVIEWER"));
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
                .originalHashValue("c".repeat(64))
                .originalStoragePath("uploads/test/" + fileName)
                .uploadedAt(LocalDateTime.now())
                .build());
    }
}
