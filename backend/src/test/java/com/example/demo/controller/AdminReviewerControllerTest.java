package com.example.demo.controller;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class AdminReviewerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String orgAdminToken;
    private User policeReviewer;
    private User otherDepartmentReviewer;
    private User etcReviewer;
    private User investigator;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .loginId("adm03")
                .email("adm03@local.dev")
                .password(passwordEncoder.encode("pass3333"))
                .name("기관관리자")
                .organizationType(OrgType.POLICE)
                .department("관리자실")
                .role(UserRole.ROLE_ORG_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        policeReviewer = userRepository.save(User.builder()
                .loginId("rev03")
                .email("rev03@local.dev")
                .password(passwordEncoder.encode("pass2222"))
                .name("경찰검토자")
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
                .name("디지털포렌식검토자")
                .organizationType(OrgType.POLICE)
                .department("디지털포렌식팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        etcReviewer = userRepository.save(User.builder()
                .loginId("rev04")
                .email("rev04@local.dev")
                .password(passwordEncoder.encode("pass2222"))
                .name("기타검토자")
                .organizationType(OrgType.ETC)
                .department("기타팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        investigator = userRepository.save(User.builder()
                .loginId("investigator03")
                .email("investigator03@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("사이버수사분석관")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        userRepository.save(User.builder()
                .loginId("pending-rev")
                .email("pending-rev@local.dev")
                .password(passwordEncoder.encode("pass2222"))
                .name("대기검토자")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.PENDING)
                .darkMode(false)
                .build());

        orgAdminToken = JwtTestSupport.loginAndGetToken(mockMvc, "adm03", "pass3333");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void listReviewers_orgAdminSeesOnlySameOrganization() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviewers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers.length()").value(2))
                .andExpect(jsonPath("$.reviewers[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                        String.valueOf(policeReviewer.getUserId()),
                        String.valueOf(otherDepartmentReviewer.getUserId())
                )));
    }

    @Test
    void listReviewers_departmentFilterNarrowsResults() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviewers")
                        .param("department", "사이버수사팀")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers.length()").value(1))
                .andExpect(jsonPath("$.reviewers[0].id").value(String.valueOf(policeReviewer.getUserId())))
                .andExpect(jsonPath("$.reviewers[0].organizationId").value("org-police"))
                .andExpect(jsonPath("$.reviewers[0].organizationName").value("경찰기관"));

        mockMvc.perform(get("/api/v1/admin/reviewers")
                        .param("department", "기타팀")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers.length()").value(0));
    }

    @Test
    void listReviewers_uploaderScopeUsesInvestigatorsOrganizationAndDepartment() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviewers")
                        .param("uploaderId", String.valueOf(investigator.getUserId()))
                        .param("department", "디지털포렌식팀")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers.length()").value(1))
                .andExpect(jsonPath("$.reviewers[0].id").value(String.valueOf(policeReviewer.getUserId())));
    }

    @Test
    void getUsersMe_includesOrganizationName() throws Exception {
        String reviewerToken = JwtTestSupport.loginAndGetToken(mockMvc, "rev03", "pass2222");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("org-police"))
                .andExpect(jsonPath("$.organizationName").value("경찰기관"))
                .andExpect(jsonPath("$.role").value("ROLE_REVIEWER"));
    }
}
