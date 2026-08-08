package com.example.demo.controller;

import com.example.demo.domain.InviteCode;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.InviteStatus;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.InviteCodeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String userToken;
    private Long pendingUserId;

    @BeforeEach
    void setUp() throws Exception {
        custodyLogRepository.deleteAll();
        inviteCodeRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .loginId("3333")
                .email("3333@local.dev")
                .password(passwordEncoder.encode("4444"))
                .name("관리자")
                .organizationType(OrgType.ETC)
                .department("관리팀")
                .role(UserRole.ROLE_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        User pendingUser = userRepository.save(User.builder()
                .loginId("pending01")
                .email("pending01@local.dev")
                .password(passwordEncoder.encode("pass1234"))
                .name("대기 사용자")
                .organizationType(OrgType.POLICE)
                .department("수사과")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.PENDING)
                .darkMode(false)
                .build());
        pendingUserId = pendingUser.getUserId();

        userRepository.save(User.builder()
                .loginId("1111")
                .email("1111@local.dev")
                .password(passwordEncoder.encode("2222"))
                .name("일반 사용자")
                .organizationType(OrgType.ETC)
                .department("로컬개발팀")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        adminToken = JwtTestSupport.loginAndGetToken(mockMvc, "3333", "4444");
        userToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");
    }

    @AfterEach
    void tearDown() {
        custodyLogRepository.deleteAll();
        inviteCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listUsers_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_withAdmin_returnsUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].username").exists())
                .andExpect(jsonPath("$.content[*].role").value(
                        org.hamcrest.Matchers.containsInAnyOrder("ORG_ADMIN", "INVESTIGATOR", "INVESTIGATOR")
                ));
    }

    @Test
    void approvePendingUser_recordsApprovedStatusAndCoC() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/approve", pendingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.processedByUserId").exists());

        User updated = userRepository.findById(pendingUserId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserStatus.APPROVED);
        assertThat(custodyLogRepository.count()).isEqualTo(1);
    }

    @Test
    void suspendApprovedUser_recordsSuspendedStatusAndBlocksLogin() throws Exception {
        User approvedUser = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElseThrow();

        mockMvc.perform(post("/api/v1/admin/users/{userId}/suspend", approvedUser.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.processedByUserId").exists());

        assertThat(userRepository.findById(approvedUser.getUserId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.SUSPENDED);
        assertThat(custodyLogRepository.findAll())
                .extracting(log -> log.getActionType())
                .contains("USER_SUSPENDED");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"1111","password":"2222"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void reactivateSuspendedUser_restoresApprovedStatusAndLogin() throws Exception {
        User approvedUser = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElseThrow();

        mockMvc.perform(post("/api/v1/admin/users/{userId}/suspend", approvedUser.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/{userId}/reactivate", approvedUser.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(custodyLogRepository.findAll())
                .extracting(log -> log.getActionType())
                .contains("USER_REACTIVATED");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"1111","password":"2222"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void rejectPendingUser_recordsRejectedStatus() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/reject", pendingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void listInviteCodes_withAdmin_returnsList() throws Exception {
        User admin = userRepository.findByLoginIdAndDeletedAtIsNull("3333").orElseThrow();
        inviteCodeRepository.save(InviteCode.builder()
                .code("VF-TEST-CODE")
                .organizationType(OrgType.ETC)
                .issuedBy(admin.getUserId())
                .status(InviteStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/api/v1/admin/invite-codes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("VF-TEST-CODE"))
                .andExpect(jsonPath("$[0].status").value("UNUSED"));
    }

    @Test
    void createInviteCode_withAdmin_persistsCode() throws Exception {
        mockMvc.perform(post("/api/v1/admin/invite-codes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expiresInDays":30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.status").value("UNUSED"));

        assertThat(inviteCodeRepository.count()).isEqualTo(1);
    }

    @Test
    void getDashboardStats_withAdmin_returnsCounts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingUsers").value(1))
                .andExpect(jsonPath("$.totalUsers").value(3));
    }

    @Test
    void getAnalysisStats_withAdmin_returnsAnalysisStats() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/analysis-stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyTotalCount").isNumber())
                .andExpect(jsonPath("$.deepfakeDetectionRate").isNumber())
                .andExpect(jsonPath("$.averageAnalysisMinutes").isNumber())
                .andExpect(jsonPath("$.weeklyPoints").isArray())
                .andExpect(jsonPath("$.weeklyPoints.length()").value(7))
                .andExpect(jsonPath("$.riskDistribution.safeCount").isNumber());
    }

    @Test
    void listLogs_withAdmin_returnsLogsAfterApproval() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/approve", pendingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].action").value("가입 승인"));
    }

    @Test
    void updateUser_withAdmin_updatesFields() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{userId}", pendingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"수정된 이름",
                                  "email":"updated@local.dev",
                                  "department":"수정부서"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("수정된 이름"));
    }

    @Test
    void deleteUser_withAdmin_softDeletesUser() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/{userId}", pendingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        User deleted = userRepository.findById(pendingUserId).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void getAdminProfile_withAdmin_returnsProfile() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("3333"));
    }

    @Test
    void updateAdminPassword_withAdmin_changesPassword() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"4444","newPassword":"55556666"}
                                """))
                .andExpect(status().isNoContent());

        assertThat(JwtTestSupport.loginAndGetToken(mockMvc, "3333", "55556666")).isNotBlank();
    }

    @Test
    void exportLogs_withAdmin_returnsCsvAttachment() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/approve", pendingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/logs/export")
                        .param("format", "csv")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("가입 승인")));
    }
}
