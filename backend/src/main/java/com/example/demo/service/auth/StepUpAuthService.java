package com.example.demo.service.auth;

import com.example.demo.config.JwtProperties;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.auth.StepUpExtendResponse;
import com.example.demo.dto.auth.StepUpVerifyResponse;
import com.example.demo.exception.AuthException;
import com.example.demo.service.custody.CustodyLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StepUpAuthService {

    private static final long EXTEND_ELIGIBLE_REMAINING_MS = 5 * 60 * 1000L;

    private final PasswordEncoder passwordEncoder;
    private final StepUpTokenRedisService stepUpTokenRedisService;
    private final CustodyLogService custodyLogService;
    private final JwtProperties jwtProperties;

    @Transactional
    public StepUpVerifyResponse verifyAndIssueToken(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_STEP_UP_PASSWORD",
                    "비밀번호가 올바르지 않습니다."
            );
        }

        String stepUpToken = stepUpTokenRedisService.issueToken(user.getUserId());
        long expiresInMs = jwtProperties.resolveStepUpExpirationMs();

        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.USER,
                user.getUserId(),
                "STEP_UP_VERIFIED",
                null,
                null,
                "민감 정보 조회용 Step-up 재인증 성공",
                null,
                null
        );

        return StepUpVerifyResponse.builder()
                .success(true)
                .stepUpToken(stepUpToken)
                .expiresIn(expiresInMs)
                .build();
    }

    @Transactional
    public StepUpExtendResponse extendToken(User user, String stepUpToken) {
        if (stepUpToken == null || stepUpToken.isBlank()) {
            throw stepUpRequired();
        }

        Long tokenUserId = stepUpTokenRedisService.resolveUserId(stepUpToken);
        if (tokenUserId == null || !tokenUserId.equals(user.getUserId())) {
            throw stepUpRequired();
        }

        Long remainingMs = stepUpTokenRedisService.resolveRemainingMs(stepUpToken);
        if (remainingMs == null || remainingMs <= 0) {
            throw stepUpRequired();
        }

        if (remainingMs > EXTEND_ELIGIBLE_REMAINING_MS) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "STEP_UP_EXTEND_TOO_EARLY",
                    "남은 시간이 5분 이하일 때만 연장할 수 있습니다."
            );
        }

        long extensionMs = jwtProperties.resolveStepUpExpirationMs();
        Long newExpiresInMs = stepUpTokenRedisService.extendToken(stepUpToken, extensionMs);
        if (newExpiresInMs == null || newExpiresInMs <= 0) {
            throw stepUpRequired();
        }

        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.USER,
                user.getUserId(),
                "STEP_UP_EXTENDED",
                null,
                null,
                "민감 정보 조회용 Step-up 세션 연장",
                null,
                null
        );

        return StepUpExtendResponse.builder()
                .success(true)
                .expiresIn(newExpiresInMs)
                .build();
    }

    public void requireValidStepUp(User user, String stepUpToken) {
        if (stepUpToken == null || stepUpToken.isBlank()) {
            throw stepUpRequired();
        }

        Long tokenUserId = stepUpTokenRedisService.resolveUserId(stepUpToken);
        if (tokenUserId == null || !tokenUserId.equals(user.getUserId())) {
            throw stepUpRequired();
        }
    }

    private static AuthException stepUpRequired() {
        return new AuthException(
                HttpStatus.FORBIDDEN,
                "STEP_UP_REQUIRED",
                "민감 정보 조회를 위해 비밀번호 재인증이 필요합니다."
        );
    }
}
