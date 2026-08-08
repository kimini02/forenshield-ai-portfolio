package com.example.demo.dto.signup;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InviteCodeValidateResponse {

    private boolean valid;
    private LocalDateTime expiresAt;
}
