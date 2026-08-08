package com.example.demo.exception;

import com.example.demo.dto.StandardErrorResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final List<StandardErrorResponse.FieldErrorDetail> details;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, Collections.emptyList());
    }

    public BusinessException(
            HttpStatus status,
            String errorCode,
            String message,
            List<StandardErrorResponse.FieldErrorDetail> details
    ) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details == null ? Collections.emptyList() : List.copyOf(details);
    }
}
