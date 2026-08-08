package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// application.yaml의 jwt.* 설정을 바인딩한다.
// JwtTokenProvider·RefreshTokenRedisService·AuthCookieSupport에서 만료 시간을 읽는다.
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    // 하위 호환 — access-expiration-ms 미설정 시 사용
    private long expirationMs;

    // 액세스 JWT 만료(ms), 기본 20분
    private long accessExpirationMs;

    // 리프레시 JWT·Redis·쿠키 만료(일), 기본 7일
    private long refreshExpirationDays;

    // Step-up 재인증 토큰 TTL(분), 기본 15분
    private long stepUpExpirationMinutes;

    // 액세스 토큰 만료(ms) — 우선순위: access-expiration-ms → expiration-ms → 20분
    public long resolveAccessExpirationMs() {
        if (accessExpirationMs > 0) {
            return accessExpirationMs;
        }
        if (expirationMs > 0) {
            return expirationMs;
        }
        return 20 * 60 * 1000L;
    }

    // 리프레시 토큰 만료(ms) — refresh-expiration-days 기준
    public long resolveRefreshExpirationMs() {
        long days = refreshExpirationDays > 0 ? refreshExpirationDays : 7;
        return days * 24 * 60 * 60 * 1000L;
    }

    public long resolveStepUpExpirationMinutes() {
        return stepUpExpirationMinutes > 0 ? stepUpExpirationMinutes : 15;
    }

    public long resolveStepUpExpirationMs() {
        return resolveStepUpExpirationMinutes() * 60 * 1000L;
    }
}
