package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends BusinessException {

    public AuthException(HttpStatus status, String errorCode, String message) {
        super(status, errorCode, message);
    }
}
