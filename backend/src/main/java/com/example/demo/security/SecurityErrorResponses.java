package com.example.demo.security;

import com.example.demo.dto.StandardErrorResponse;

public final class SecurityErrorResponses {

    private SecurityErrorResponses() {
    }

    public static StandardErrorResponse unauthorized() {
        return StandardErrorResponse.builder()
                .success(false)
                .errorCode("UNAUTHORIZED")
                .message("인증이 필요합니다. 로그인 후 다시 시도해 주세요.")
                .build();
    }

    public static StandardErrorResponse forbidden() {
        return StandardErrorResponse.builder()
                .success(false)
                .errorCode("FORBIDDEN")
                .message("이 요청을 수행할 권한이 없습니다.")
                .build();
    }
}
