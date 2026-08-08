package com.example.demo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LoginRateLimitException extends AuthException {

    private final long retryAfterSeconds;

    private LoginRateLimitException(HttpStatus status, String errorCode, String message, long retryAfterSeconds) {
        super(status, errorCode, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static LoginRateLimitException temporarilyBlocked(long retryAfterSeconds) {
        return new LoginRateLimitException(
                HttpStatus.TOO_MANY_REQUESTS,
                "LOGIN_TEMPORARILY_BLOCKED",
                "로그인 시도 횟수를 초과했습니다. 3분 후 다시 시도해 주세요.",
                Math.max(1, retryAfterSeconds)
        );
    }

    public static LoginRateLimitException dayBlocked(long retryAfterSeconds) {
        return new LoginRateLimitException(
                HttpStatus.TOO_MANY_REQUESTS,
                "LOGIN_DAY_BLOCKED",
                "횟수 제한으로 인해 24시간 동안 로그인이 제한됩니다.",
                Math.max(1, retryAfterSeconds)
        );
    }
}
