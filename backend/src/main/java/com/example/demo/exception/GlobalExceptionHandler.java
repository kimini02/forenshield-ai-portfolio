package com.example.demo.exception;

import com.example.demo.dto.StandardErrorResponse;
import com.example.demo.security.SecurityErrorResponses;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StandardErrorResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(toErrorResponse(ex));
    }

    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<StandardErrorResponse> handleLoginRateLimit(LoginRateLimitException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSeconds() > 0) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return builder.body(toErrorResponse(ex));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<StandardErrorResponse> handleAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SecurityErrorResponses.forbidden());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<StandardErrorResponse.FieldErrorDetail> details = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> StandardErrorResponse.FieldErrorDetail.builder()
                        .field(error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName())
                        .reason(error.getDefaultMessage())
                        .build())
                .toList();

        return ResponseEntity.badRequest().body(
                StandardErrorResponse.builder()
                        .success(false)
                        .errorCode("VALIDATION_ERROR")
                        .message("입력값을 확인해주세요.")
                        .details(details)
                        .build()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<StandardErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<StandardErrorResponse.FieldErrorDetail> details = ex.getConstraintViolations()
                .stream()
                .map(this::toFieldErrorDetail)
                .toList();

        return ResponseEntity.badRequest().body(
                StandardErrorResponse.builder()
                        .success(false)
                        .errorCode("VALIDATION_ERROR")
                        .message("입력값을 확인해주세요.")
                        .details(details)
                        .build()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<StandardErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(
                StandardErrorResponse.builder()
                        .success(false)
                        .errorCode("VALIDATION_ERROR")
                        .message("요청 값이 올바르지 않습니다.")
                        .build()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<StandardErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                StandardErrorResponse.builder()
                        .success(false)
                        .errorCode("FILE_TOO_LARGE")
                        .message("업로드 가능한 최대 파일 크기를 초과했습니다.")
                        .build()
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<StandardErrorResponse> handleMultipart(MultipartException ex) {
        if (ex.getCause() instanceof MaxUploadSizeExceededException) {
            return handleMaxUploadSize((MaxUploadSizeExceededException) ex.getCause());
        }
        return ResponseEntity.badRequest().body(
                StandardErrorResponse.builder()
                        .success(false)
                        .errorCode("UPLOAD_FAILED")
                        .message("파일 업로드 처리 중 오류가 발생했습니다.")
                        .build()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<StandardErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                StandardErrorResponse.builder()
                        .success(false)
                        .errorCode("NOT_FOUND")
                        .message("요청한 API를 찾을 수 없습니다.")
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                StandardErrorResponse.builder()
                        .success(false)
                        .errorCode("INTERNAL_ERROR")
                        .message("서버 오류가 발생했습니다.")
                        .build()
        );
    }

    private StandardErrorResponse toErrorResponse(BusinessException ex) {
        StandardErrorResponse.StandardErrorResponseBuilder builder = StandardErrorResponse.builder()
                .success(false)
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage());

        if (!ex.getDetails().isEmpty()) {
            builder.details(ex.getDetails());
        }
        return builder.build();
    }

    private StandardErrorResponse.FieldErrorDetail toFieldErrorDetail(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() == null
                ? "request"
                : violation.getPropertyPath().toString();
        int lastDot = field.lastIndexOf('.');
        if (lastDot >= 0) {
            field = field.substring(lastDot + 1);
        }
        return StandardErrorResponse.FieldErrorDetail.builder()
                .field(field)
                .reason(violation.getMessage())
                .build();
    }
}
