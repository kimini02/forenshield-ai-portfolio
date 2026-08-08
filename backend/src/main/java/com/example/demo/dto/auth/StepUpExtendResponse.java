package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StepUpExtendResponse {

    private boolean success;
    /** 연장 후 남은 유효 시간(ms) — 프론트 카운트다운 기준 */
    private long expiresIn;
}
