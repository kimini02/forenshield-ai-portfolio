package com.example.demo.security;

import com.example.demo.exception.LoginRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimitServiceTest {

    private MutableClock clock;
    private LoginRateLimitService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-07-10T00:00:00Z"));
        service = new LoginRateLimitService(clock);
    }

    @Test
    @DisplayName("5회 실패 전에는 로그인 시도를 막지 않는다")
    void allowsAttemptsBeforeThreshold() {
        for (int i = 0; i < 4; i++) {
            service.recordFailedAttempt("203.0.113.10");
            assertDoesNotThrow(() -> service.assertAllowed("203.0.113.10"));
        }
    }

    @Test
    @DisplayName("5회 연속 실패 시 3분간 차단된다")
    void blocksForThreeMinutesAfterFiveFailures() {
        failFiveTimes("203.0.113.11");

        LoginRateLimitException ex = assertThrows(
                LoginRateLimitException.class,
                () -> service.assertAllowed("203.0.113.11")
        );
        assertEquals("LOGIN_TEMPORARILY_BLOCKED", ex.getErrorCode());
        assertEquals(180, ex.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("3분 대기 후 5회 더 실패하면 24시간 차단된다")
    void blocksForTwentyFourHoursAfterSecondStage() {
        failFiveTimes("203.0.113.12");
        clock.advance(Duration.ofMinutes(3));

        failFiveTimes("203.0.113.12");
        LoginRateLimitException ex = assertThrows(
                LoginRateLimitException.class,
                () -> service.assertAllowed("203.0.113.12")
        );
        assertEquals("LOGIN_DAY_BLOCKED", ex.getErrorCode());
        assertEquals(Duration.ofHours(24).toSeconds(), ex.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("24시간 차단이 끝나면 다시 로그인을 시도할 수 있다")
    void resetsAfterTwentyFourHourBlockExpires() {
        String ip = "203.0.113.13";
        failFiveTimes(ip);
        clock.advance(Duration.ofMinutes(3));
        failFiveTimes(ip);
        clock.advance(Duration.ofHours(24));

        assertDoesNotThrow(() -> service.assertAllowed(ip));
        service.recordFailedAttempt(ip);
        assertDoesNotThrow(() -> service.assertAllowed(ip));
    }

    @Test
    @DisplayName("로그인 성공 시 실패 이력이 초기화된다")
    void resetsAfterSuccessfulLogin() {
        failFiveTimes("203.0.113.14");
        service.recordSuccessfulLogin("203.0.113.14");

        assertDoesNotThrow(() -> service.assertAllowed("203.0.113.14"));
        service.recordFailedAttempt("203.0.113.14");
        assertDoesNotThrow(() -> service.assertAllowed("203.0.113.14"));
    }

    private void failFiveTimes(String ip) {
        for (int i = 0; i < LoginRateLimitService.MAX_FAILURES_PER_STAGE; i++) {
            service.recordFailedAttempt(ip);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
