package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class FileSizeExceededException extends BusinessException {

    public FileSizeExceededException(String message) {
        super(HttpStatus.BAD_REQUEST, "FILE_SIZE_EXCEEDED", message);
    }
}
