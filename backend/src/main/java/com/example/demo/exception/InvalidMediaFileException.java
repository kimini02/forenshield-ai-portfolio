package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class InvalidMediaFileException extends BusinessException {

    private static final String MESSAGE = "손상되었거나 읽을 수 없는 미디어 파일입니다.";

    public InvalidMediaFileException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_FILE", MESSAGE);
    }

    public InvalidMediaFileException(Throwable cause) {
        super(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_FILE", MESSAGE);
        initCause(cause);
    }
}
