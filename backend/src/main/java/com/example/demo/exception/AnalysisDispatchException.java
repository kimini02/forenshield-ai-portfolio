package com.example.demo.exception;

public class AnalysisDispatchException extends RuntimeException {

    private final String errorCode;

    public AnalysisDispatchException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AnalysisDispatchException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
