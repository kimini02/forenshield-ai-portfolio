package com.example.demo.security;

import com.example.demo.config.JwtProperties;
import com.example.demo.domain.User;
import com.example.demo.dto.TokenResponse;
import com.example.demo.util.UserRoleSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

// 액세스·리프레시 JWT 생성·검증을 담당한다.
// AuthService(로그인·재발급·로그아웃)와 JwtAuthenticationFilter(API 인증)에서 사용한다.
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String CLAIM_LOGIN_ID = "loginId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_USER_ID = "userId";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;

    // 하위 호환 — 기존 createToken 호출부는 액세스 JWT만 생성
    public String createToken(User user) {
        return createAccessToken(user);
    }

    // 로그인·재발급 시 액세스+리프레시 쌍을 한 번에 만든다
    public TokenResponse createTokenResponse(User user) {
        long accessExpirationMs = jwtProperties.resolveAccessExpirationMs();
        String accessToken = buildAccessToken(user, accessExpirationMs);
        String refreshToken = buildRefreshToken(user.getUserId());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessExpirationMs)
                .build();
    }

    // API Bearer 헤더용 짧은 액세스 JWT
    public String createAccessToken(User user) {
        return buildAccessToken(user, jwtProperties.resolveAccessExpirationMs());
    }

    // HttpOnly 쿠키·Redis 저장용 긴 리프레시 JWT
    public String createRefreshToken(Long userId) {
        return buildRefreshToken(userId);
    }

    // 서명·만료 포함 토큰 유효성 검사 (재발급·로그아웃 시 사용)
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    // ACCESS / REFRESH 구분
    public String getTokenType(String token) {
        return parseClaims(token).get(CLAIM_TYPE, String.class);
    }

    // 로그아웃·재발급 시 사용자 PK 추출
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        Long userId = claims.get(CLAIM_USER_ID, Long.class);
        if (userId != null) {
            return userId;
        }
        return Long.parseLong(claims.getSubject());
    }

    // JwtAuthenticationFilter에서 액세스 JWT 파싱
    public Optional<Claims> parseToken(String token) {
        try {
            return Optional.of(parseClaims(token));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    // 리프레시 토큰이 Bearer로 오는 경우 인증 제외
    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    private String buildAccessToken(User user, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_LOGIN_ID, user.getLoginId())
                .claim(CLAIM_ROLE, UserRoleSupport.toApiRole(user.getRole()).name())
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    private String buildRefreshToken(Long userId) {
        Date now = new Date();
        long expirationMs = jwtProperties.resolveRefreshExpirationMs();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
