package com.example.demo.exception;

import lombok.Getter;

@Getter
public class AnalysisCopyException extends RuntimeException {

    private final String errorCode;
    private final String step;

    public AnalysisCopyException(String step, String errorCode, String message) {
        super(message);
        this.step = step;
        this.errorCode = errorCode;
    }

    public AnalysisCopyException(String step, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.errorCode = errorCode;
    }
}
