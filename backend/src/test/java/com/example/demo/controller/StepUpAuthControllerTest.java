package com.example.demo.controller;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SignupRateLimitService;
import com.example.demo.support.JwtTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.demo.support.StepUpTestSupport;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StepUpAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SignupRateLimitService signupRateLimitService;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        signupRateLimitService.reset();
        custodyLogRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .loginId("1111")
                .email("1111@test.local")
                .password(passwordEncoder.encode("2222"))
                .name("테스트 사용자")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        accessToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");
    }

    @AfterEach
    void tearDown() {
        custodyLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("올바른 비밀번호로 stepUpToken과 expiresIn을 발급한다")
    void verifyStepUp_success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/step-up/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"2222"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.stepUpToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900000));

        User user = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElseThrow();
        assertThat(custodyLogRepository.findAll())
                .anyMatch(log -> "STEP_UP_VERIFIED".equals(log.getActionType())
                        && CustodyTargetType.USER.equals(log.getTargetType())
                        && user.getUserId().equals(log.getTargetId()));
    }

    @Test
    @DisplayName("잘못된 비밀번호는 401 INVALID_STEP_UP_PASSWORD를 반환한다")
    void verifyStepUp_invalidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/step-up/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STEP_UP_PASSWORD"));
    }

    @Test
    @DisplayName("JWT 없이 호출하면 401을 반환한다")
    void verifyStepUp_unauthorizedWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/step-up/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"2222"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("1분에 5회 초과 시 429 RATE_LIMIT_EXCEEDED를 반환한다")
    void verifyStepUp_tooManyRequests_returnsRateLimitError() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/step-up/verify")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"password":"wrong-password"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/step-up/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"wrong-password"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Step-up 발급 직후 연장 요청은 400 STEP_UP_EXTEND_TOO_EARLY")
    void extendStepUp_tooEarly() throws Exception {
        String stepUpToken = StepUpTestSupport.issueStepUpToken(mockMvc, accessToken, "2222");

        mockMvc.perform(post("/api/v1/auth/step-up/extend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STEP_UP_EXTEND_TOO_EARLY"));
    }
}
