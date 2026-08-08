package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class HashGenerationException extends BusinessException {

    public HashGenerationException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_GENERATION_FAILED", message);
    }

    public HashGenerationException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_GENERATION_FAILED", message);
        initCause(cause);
    }
}
