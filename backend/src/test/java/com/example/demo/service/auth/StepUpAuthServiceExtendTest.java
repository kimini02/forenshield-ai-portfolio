package com.example.demo.service.auth;

import com.example.demo.config.JwtProperties;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.exception.AuthException;
import com.example.demo.service.custody.CustodyLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepUpAuthServiceExtendTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StepUpTokenRedisService stepUpTokenRedisService;

    @Mock
    private CustodyLogService custodyLogService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private StepUpAuthService stepUpAuthService;

    private User user;

    @BeforeEach
    void setUp() throws Exception {
        user = User.builder()
                .loginId("1111")
                .email("1111@test.local")
                .password("encoded")
                .name("테스트")
                .organizationType(OrgType.ETC)
                .department("테스트")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build();
        ReflectionTestUtils.setField(user, "userId", 10L);
    }

    @Test
    @DisplayName("남은 시간이 5분 초과면 STEP_UP_EXTEND_TOO_EARLY")
    void extendToken_tooEarly() {
        when(stepUpTokenRedisService.resolveUserId("token")).thenReturn(10L);
        when(stepUpTokenRedisService.resolveRemainingMs("token")).thenReturn(6 * 60 * 1000L);

        assertThatThrownBy(() -> stepUpAuthService.extendToken(user, "token"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo("STEP_UP_EXTEND_TOO_EARLY");
    }

    @Test
    @DisplayName("남은 시간이 5분 이하이면 15분 연장 후 expiresIn 반환")
    void extendToken_success() {
        when(jwtProperties.resolveStepUpExpirationMs()).thenReturn(15 * 60 * 1000L);
        when(stepUpTokenRedisService.resolveUserId("token")).thenReturn(10L);
        when(stepUpTokenRedisService.resolveRemainingMs("token")).thenReturn(4 * 60 * 1000L);
        when(stepUpTokenRedisService.extendToken(eq("token"), anyLong())).thenReturn(19 * 60 * 1000L);

        var response = stepUpAuthService.extendToken(user, "token");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExpiresIn()).isEqualTo(19 * 60 * 1000L);
        verify(custodyLogService).record(
                eq(10L),
                eq(com.example.demo.domain.enums.CustodyTargetType.USER),
                eq(10L),
                eq("STEP_UP_EXTENDED"),
                eq(null),
                eq(null),
                eq("민감 정보 조회용 Step-up 세션 연장"),
                eq(null),
                eq(null)
        );
    }
}
