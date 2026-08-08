package com.example.demo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMaxUploadSize_returnsFileTooLarge() {
        var response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void handleNoResourceFound_returnsNotFound() {
        var response = handler.handleNoResourceFound(
                new NoResourceFoundException(org.springframework.http.HttpMethod.POST, "/api/v1/auth/step-up/extend")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("요청한 API를 찾을 수 없습니다.");
    }

    @Test
    void handleUnexpected_returnsInternalErrorWithoutExposingDetails() {
        var response = handler.handleUnexpected(new RuntimeException("db password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("서버 오류가 발생했습니다.");
        assertThat(response.getBody().getMessage()).doesNotContain("password");
    }

    @Test
    void handleInvalidMediaFile_returnsBadRequest() {
        var response = handler.handleBusinessException(new InvalidMediaFileException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_MEDIA_FILE");
        assertThat(response.getBody().getMessage()).isEqualTo("손상되었거나 읽을 수 없는 미디어 파일입니다.");
    }

    @Test
    void handleAccessDenied_returnsRoleNeutralForbidden() {
        var response = handler.handleAccessDenied();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().getMessage()).isEqualTo("이 요청을 수행할 권한이 없습니다.");
    }
}
