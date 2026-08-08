package com.example.demo.exception;

import com.example.demo.dto.StandardErrorResponse;
import org.springframework.http.HttpStatus;

import java.util.List;

public class DuplicateSignupFieldException extends BusinessException {

    public DuplicateSignupFieldException(String field, String message) {
        super(
                HttpStatus.CONFLICT,
                resolveErrorCode(field),
                message,
                List.of(StandardErrorResponse.FieldErrorDetail.builder()
                        .field(field)
                        .reason(message)
                        .build())
        );
    }

    private static String resolveErrorCode(String field) {
        return "email".equals(field) ? "DUPLICATE_EMAIL" : "DUPLICATE_LOGIN_ID";
    }
}
