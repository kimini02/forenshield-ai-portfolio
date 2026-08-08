package com.example.demo.dto;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.util.UserRoleSupport;
import lombok.Builder;
import lombok.Getter;

// 로그인·재발급 API 응답. 액세스 JWT는 본문, 리프레시 JWT는 HttpOnly 쿠키로만 전달한다.
@Getter
@Builder
public class LoginResponse {

    private final boolean success;

    // 하위 호환 — accessToken과 동일 값
    @Deprecated
    private final String token;

    // API Bearer 헤더용 액세스 JWT
    private final String accessToken;

    // 액세스 만료까지 남은 시간(ms)
    private final Long accessTokenExpiresIn;

    private final Long userId;
    private final String loginId;
    private final String name;
    private final UserRole role;

    // AuthService 결과를 프론트 응답 형식으로 변환
    public static LoginResponse from(User user, TokenResponse tokens) {
        String accessToken = tokens.getAccessToken();
        return LoginResponse.builder()
                .success(true)
                .token(accessToken)
                .accessToken(accessToken)
                .accessTokenExpiresIn(tokens.getAccessTokenExpiresIn())
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .name(user.getName())
                .role(UserRoleSupport.toApiRole(user.getRole()))
                .build();
    }
}
