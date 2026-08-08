package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Getter;

// Response 
// 15분짜리 StepupToken 줄게 (발급)
@Getter
@Builder
public class StepUpVerifyResponse {

    private boolean success;  // 재인증 성공 여부
    private String stepUpToken; // 15분동안 쓸 Step-up 토큰(증거 상세 API 헤더에 첨부)
    private long expiresIn;  // 유효 시간 ms , 900000 = 15분 (프론트 카운트다운에 사용)
}
