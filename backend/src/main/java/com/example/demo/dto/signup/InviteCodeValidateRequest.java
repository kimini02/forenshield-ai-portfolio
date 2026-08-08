package com.example.demo.dto.signup;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InviteCodeValidateRequest {

    @NotBlank(message = "초대코드는 필수입니다.")
    private String code;
}
