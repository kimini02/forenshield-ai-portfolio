package com.example.demo.exception;

import com.example.demo.dto.StandardErrorResponse;
import org.springframework.http.HttpStatus;

import java.util.List;

public class InvalidInviteCodeException extends BusinessException {

    public InvalidInviteCodeException(String message) {
        super(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                List.of(StandardErrorResponse.FieldErrorDetail.builder()
                        .field("inviteCode")
                        .reason(message)
                        .build())
        );
    }
}
