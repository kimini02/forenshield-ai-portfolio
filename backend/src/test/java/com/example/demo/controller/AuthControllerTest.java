package com.example.demo.controller;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.LoginRateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimitService loginRateLimitService;

    @BeforeEach
    void setUp() {
        loginRateLimitService.clearClientState("127.0.0.1");
        userRepository.deleteAll();
        userRepository.save(createUser("1111", "2222", UserRole.ROLE_USER, UserStatus.APPROVED));
        userRepository.save(createUser("3333", "4444", UserRole.ROLE_ADMIN, UserStatus.APPROVED));
        userRepository.save(createUser("5555", "6666", UserRole.ROLE_USER, UserStatus.PENDING));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("승인된 일반 사용자 로그인 성공")
    void loginSuccessAsUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"1111","password":"2222"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").value(not("")))
                .andExpect(jsonPath("$.loginId").value("1111"))
                .andExpect(jsonPath("$.role").value("ROLE_INVESTIGATOR"));
    }

    @Test
    @DisplayName("승인된 관리자 로그인 성공")
    void loginSuccessAsAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"3333","password":"4444"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ORG_ADMIN"));
    }

    @Test
    @DisplayName("비밀번호 불일치 시 401")
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"1111","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("승인 대기 사용자는 401 + ACCOUNT_PENDING")
    void loginFailsWhenPending() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"5555","password":"6666"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_PENDING"))
                .andExpect(jsonPath("$.message").value("관리자 승인 대기 중입니다. 승인 후 로그인할 수 있습니다."));
    }

    @Test
    @DisplayName("로그인 5회 실패 시 429 + LOGIN_TEMPORARILY_BLOCKED")
    void loginTemporarilyBlockedAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"1111","password":"wrong"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"1111","password":"wrong"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("LOGIN_TEMPORARILY_BLOCKED"));
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
            "auth.refresh.enabled=false"
    })
    @AutoConfigureMockMvc
    class WhenRefreshDisabled {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("리프레시 비활성화 시 /api/auth/refresh 는 401")
        void refreshFailsWhenDisabled() throws Exception {
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("REFRESH_DISABLED"));
        }
    }

    private User createUser(String loginId, String rawPassword, UserRole role, UserStatus status) {
        return User.builder()
                .loginId(loginId)
                .email(loginId + "@test.local")
                .password(passwordEncoder.encode(rawPassword))
                .name("테스트-" + loginId)
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(role)
                .status(status)
                .darkMode(false)
                .build();
    }
}
