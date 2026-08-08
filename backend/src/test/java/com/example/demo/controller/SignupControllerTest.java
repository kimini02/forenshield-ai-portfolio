package com.example.demo.controller;

import com.example.demo.domain.InviteCode;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.InviteStatus;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.InviteCodeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SignupRateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class SignupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SignupRateLimitService signupRateLimitService;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        inviteCodeRepository.deleteAll();
        signupRateLimitService.reset();
    }

    @Test
    void signup_success_createsPendingUserAndConsumesInviteCode() throws Exception {
        inviteCodeRepository.save(activeInviteCode("VF-A3K9-7M2P"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("kimminhee", "kim@example.go.kr", "VF-A3K9-7M2P")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다."));

        User savedUser = userRepository.findAll().get(0);
        assertThat(savedUser.getLoginId()).isEqualTo("kimminhee");
        assertThat(savedUser.getName()).isEqualTo("김민희");
        assertThat(savedUser.getOrganizationType()).isEqualTo(OrgType.POLICE);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(savedUser.getDarkMode()).isFalse();
        assertThat(passwordEncoder.matches("Password123!", savedUser.getPassword())).isTrue();

        InviteCode inviteCode = inviteCodeRepository.findByCode("VF-A3K9-7M2P").orElseThrow();
        assertThat(inviteCode.getStatus()).isEqualTo(InviteStatus.USED);
        assertThat(inviteCode.getUsedBy()).isEqualTo(savedUser.getUserId());
        assertThat(inviteCode.getUsedAt()).isNotNull();
    }

    @Test
    void signup_duplicateLoginId_returnsConflict() throws Exception {
        InviteCode firstCode = inviteCodeRepository.save(activeInviteCode("VF-FIRST-0001"));
        userRepository.save(User.builder()
                .loginId("kimminhee")
                .email("old@example.go.kr")
                .password(passwordEncoder.encode("Password123!"))
                .name("기존사용자")
                .organizationType(OrgType.POLICE)
                .department("서울경찰청 사이버수사과")
                .inviteCode(firstCode)
                .build());
        inviteCodeRepository.save(activeInviteCode("VF-SECOND-0002"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("kimminhee", "kim@example.go.kr", "VF-SECOND-0002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_LOGIN_ID"))
                .andExpect(jsonPath("$.details[0].field").value("loginId"));
    }

    @Test
    void signup_passwordWithoutSpecialCharacter_returnsBadRequest() throws Exception {
        inviteCodeRepository.save(activeInviteCode("VF-A3K9-7M2P"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("kimminhee", "kim@example.go.kr", "Password123", "VF-A3K9-7M2P")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("password"));
    }

    @Test
    void checkUsername_returnsAvailability() throws Exception {
        mockMvc.perform(get("/api/v1/auth/username/check")
                        .param("loginId", "availableUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void validateInviteCode_doesNotConsumeCode() throws Exception {
        inviteCodeRepository.save(activeInviteCode("VF-A3K9-7M2P"));

        mockMvc.perform(post("/api/v1/invite-codes/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"VF-A3K9-7M2P"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThat(inviteCodeRepository.findByCode("VF-A3K9-7M2P").orElseThrow().getStatus())
                .isEqualTo(InviteStatus.ACTIVE);
    }

    @Test
    void validateInviteCode_usedCode_returnsFalse() throws Exception {
        inviteCodeRepository.save(InviteCode.builder()
                .code("VF-USED-0001")
                .organizationType(OrgType.POLICE)
                .issuedBy(1L)
                .status(InviteStatus.USED)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build());

        mockMvc.perform(post("/api/v1/invite-codes/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"VF-USED-0001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void departments_returnsOrganizationDepartments() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/departments")
                        .param("organizationType", "POLICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[0]").value("서울경찰청 사이버수사과"));
    }

    @Test
    void signup_tooManyRequests_returnsRateLimitError() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
    }

    private InviteCode activeInviteCode(String code) {
        return InviteCode.builder()
                .code(code)
                .organizationType(OrgType.POLICE)
                .issuedBy(1L)
                .status(InviteStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
    }

    private String signupJson(String loginId, String email, String inviteCode) {
        return signupJson(loginId, email, "Password123!", inviteCode);
    }

    private String signupJson(String loginId, String email, String password, String inviteCode) {
        return """
                {
                  "loginId": "%s",
                  "password": "%s",
                  "displayName": "김민희",
                  "organizationType": "POLICE",
                  "department": "서울경찰청 사이버수사과",
                  "position": "디지털 증거 분석 담당자",
                  "email": "%s",
                  "phone": "010-0000-0000",
                  "inviteCode": "%s",
                  "agreements": {
                    "terms": true,
                    "privacy": true,
                    "security": true,
                    "log": false
                  }
                }
                """.formatted(loginId, password, email, inviteCode);
    }
}
