package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

// JwtTokenProvider가 액세스·리프레시 JWT를 한 번에 만들 때 AuthService 내부에서 쓰는 DTO.
// AuthController는 리프레시는 AuthCookieSupport(HttpOnly 쿠키), 액세스는 LoginResponse 본문으로 내려준다.
@Getter
@Builder
public class TokenResponse {

    // API 호출용 짧은 JWT — 프론트는 Authorization Bearer 헤더로 전달
    private final String accessToken;

    // 재발급용 긴 JWT — 서버 Redis + HttpOnly 쿠키에만 보관, 프론트 본문에는 null로 내려도 됨
    private final String refreshToken;

    @Builder.Default
    private final String grantType = "Bearer";

    // 액세스 토큰 만료까지 남은 시간(ms) — 프론트 자동 재발급·타이머용
    private final Long accessTokenExpiresIn;
}
