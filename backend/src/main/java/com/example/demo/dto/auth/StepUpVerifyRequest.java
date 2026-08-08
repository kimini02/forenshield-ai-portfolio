package com.example.demo.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Request
// 내 비밀번호 이거 맞아? (입력)
@Getter
@Setter
@NoArgsConstructor
public class StepUpVerifyRequest {

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
