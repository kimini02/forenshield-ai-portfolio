package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class AdminException extends BusinessException {

    public AdminException(HttpStatus status, String message) {
        this(status, "ADMIN_ERROR", message);
    }

    public AdminException(HttpStatus status, String errorCode, String message) {
        super(status, errorCode, message);
    }
}
