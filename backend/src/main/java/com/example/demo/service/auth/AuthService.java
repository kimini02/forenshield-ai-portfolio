package com.example.demo.service.auth;

import com.example.demo.config.AuthRefreshProperties;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.AuthenticatedTokens;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.TokenResponse;
import com.example.demo.exception.AuthException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.security.LoginRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인·재발급·로그아웃 비즈니스 로직. 승인 대기(PENDING) 등 기존 계정 정책을 유지한다.
// JwtTokenProvider(토큰 발급) + RefreshTokenRedisService(Redis 저장) + AuthController(쿠키)와 연결된다.
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisService refreshTokenRedisService;
    private final AuthRefreshProperties authRefreshProperties;
    private final LoginRateLimitService loginRateLimitService;

    // ID·비밀번호 검증 → 승인 계정만 → JWT 쌍 발급 → Redis에 리프레시 저장
    @Transactional(readOnly = true)
    public AuthenticatedTokens login(LoginRequest request, String clientIp) {
        loginRateLimitService.assertAllowed(clientIp);

        User user = userRepository.findByLoginIdAndDeletedAtIsNull(request.getLoginId())
                .orElseThrow(() -> {
                    loginRateLimitService.recordFailedAttempt(clientIp);
                    return invalidCredentials();
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginRateLimitService.recordFailedAttempt(clientIp);
            throw invalidCredentials();
        }

        validateApprovedStatus(user);

        TokenResponse tokens = jwtTokenProvider.createTokenResponse(user);
        if (authRefreshProperties.isEnabled()) {
            refreshTokenRedisService.saveRefreshToken(user.getUserId(), tokens.getRefreshToken());
        }
        loginRateLimitService.recordSuccessfulLogin(clientIp);
        return new AuthenticatedTokens(user, tokens);
    }

    // 쿠키의 리프레시 JWT 검증 → Redis 일치 확인 → 새 JWT 쌍 발급(rotation)
    @Transactional(readOnly = true)
    public AuthenticatedTokens reissue(String refreshToken) {
        if (!authRefreshProperties.isEnabled()) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_DISABLED",
                    "자동 로그인이 비활성화되어 있습니다. 다시 로그인해 주세요."
            );
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN",
                    "리프레시 토큰이 없습니다."
            );
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN",
                    "유효하지 않거나 만료된 리프레시 토큰입니다."
            );
        }

        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN",
                    "리프레시 토큰이 아닙니다."
            );
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        if (!refreshTokenRedisService.matchesRefreshToken(userId, refreshToken)) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN",
                    "리프레시 토큰이 일치하지 않습니다."
            );
        }

        User user = userRepository.findById(userId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."
                ));

        validateApprovedStatus(user);

        TokenResponse newTokens = jwtTokenProvider.createTokenResponse(user);
        refreshTokenRedisService.saveRefreshToken(userId, newTokens.getRefreshToken());
        return new AuthenticatedTokens(user, newTokens);
    }

    // 액세스 헤더 또는 리프레시 쿠키로 사용자 식별 후 Redis에서 리프레시 삭제
    @Transactional
    public void logout(String authorizationHeader, String refreshTokenCookie) {
        Long userId = resolveUserIdForLogout(authorizationHeader, refreshTokenCookie);
        if (userId != null) {
            refreshTokenRedisService.deleteRefreshToken(userId);
        }
    }

    // Bearer 액세스 우선, 없으면 리프레시 쿠키로 userId 추출
    private Long resolveUserIdForLogout(String authorizationHeader, String refreshTokenCookie) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring(7).trim();
            if (jwtTokenProvider.validateToken(accessToken)
                    && JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(jwtTokenProvider.getTokenType(accessToken))) {
                return jwtTokenProvider.getUserIdFromToken(accessToken);
            }
        }

        if (refreshTokenCookie != null && !refreshTokenCookie.isBlank()
                && jwtTokenProvider.validateToken(refreshTokenCookie)
                && JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(jwtTokenProvider.getTokenType(refreshTokenCookie))) {
            return jwtTokenProvider.getUserIdFromToken(refreshTokenCookie);
        }

        return null;
    }

    // APPROVED만 로그인·재발급 허용 — 가입 승인 정책 유지
    private void validateApprovedStatus(User user) {
        if (user.getStatus() == UserStatus.APPROVED) {
            return;
        }

        String errorCode = switch (user.getStatus()) {
            case PENDING -> "ACCOUNT_PENDING";
            case REJECTED -> "ACCOUNT_REJECTED";
            case SUSPENDED -> "ACCOUNT_SUSPENDED";
            default -> "ACCOUNT_NOT_APPROVED";
        };

        String message = switch (user.getStatus()) {
            case PENDING -> "관리자 승인 대기 중입니다. 승인 후 로그인할 수 있습니다.";
            case REJECTED -> "가입이 반려되었습니다. 관리자에게 문의해 주세요.";
            case SUSPENDED -> "계정이 정지되었습니다. 관리자에게 문의해 주세요.";
            default -> "로그인할 수 없는 계정 상태입니다.";
        };

        throw new AuthException(HttpStatus.UNAUTHORIZED, errorCode, message);
    }

    private AuthException invalidCredentials() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "사번 또는 비밀번호가 올바르지 않습니다."
        );
    }
}
