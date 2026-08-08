package com.example.demo.dto.signup;

import com.example.demo.domain.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupResponse {

    private String userId;
    private UserStatus status;
    private String message;
}
