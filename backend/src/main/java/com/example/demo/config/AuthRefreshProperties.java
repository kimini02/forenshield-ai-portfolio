package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 리프레시 JWT 자동 재발급 정책.
 * enabled=true(기본): HttpOnly refresh 쿠키 + /api/auth/refresh로 XSS 대비 액세스 JWT 재발급.
 * idleTimeoutMinutes: Redis 비활성 TTL — 재발급 시 갱신(sliding). Access JWT보다 길게(기본 90분).
 * 초과 시 재로그인 필요.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.refresh")
public class AuthRefreshProperties {

    private boolean enabled = true;

    /** Redis idle TTL(분). 마지막 refresh 이후 이 시간이 지나면 세션 만료. Access JWT보다 길게 유지. */
    private int idleTimeoutMinutes = 90;
}
