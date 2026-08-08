package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedFileTypeException extends BusinessException {

    public UnsupportedFileTypeException(String message) {
        super(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE", message);
    }
}
