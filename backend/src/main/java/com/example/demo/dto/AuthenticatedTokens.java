package com.example.demo.dto;

import com.example.demo.domain.User;

// AuthService 로그인·재발급 결과 — 사용자 정보 + access·refresh JWT 쌍
public record AuthenticatedTokens(User user, TokenResponse tokens) {
}
