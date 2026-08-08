package com.example.demo.security;

import com.example.demo.exception.LoginRateLimitException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginRateLimitService {

    static final int MAX_FAILURES_PER_STAGE = 5;
    private static final Duration FIRST_LOCK = Duration.ofMinutes(3);
    private static final Duration SECOND_LOCK = Duration.ofHours(24);

    private final Clock clock;
    private final ConcurrentHashMap<String, ClientState> states = new ConcurrentHashMap<>();

    public LoginRateLimitService() {
        this(Clock.systemUTC());
    }

    LoginRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public void assertAllowed(String clientIp) {
        ClientState state = states.get(normalizeIp(clientIp));
        if (state == null) {
            return;
        }
        state.assertAllowed(clock.instant());
    }

    public void recordFailedAttempt(String clientIp) {
        String ip = normalizeIp(clientIp);
        states.compute(ip, (ignored, existing) -> {
            ClientState state = existing == null ? new ClientState() : existing;
            state.recordFailure(clock.instant());
            return state;
        });
    }

    public void recordSuccessfulLogin(String clientIp) {
        states.remove(normalizeIp(clientIp));
    }

    public void clearClientState(String clientIp) {
        states.remove(normalizeIp(clientIp));
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }

    static final class ClientState {
        int stage;
        int failureCount;
        Instant blockedUntil;

        void assertAllowed(Instant now) {
            if (blockedUntil == null) {
                return;
            }
            if (now.isBefore(blockedUntil)) {
                long retryAfterSeconds = Math.max(1, Duration.between(now, blockedUntil).toSeconds());
                if (stage >= 2) {
                    throw LoginRateLimitException.dayBlocked(retryAfterSeconds);
                }
                throw LoginRateLimitException.temporarilyBlocked(retryAfterSeconds);
            }

            blockedUntil = null;
            failureCount = 0;
            if (stage >= 2) {
                stage = 0;
            }
        }

        void recordFailure(Instant now) {
            assertAllowed(now);
            failureCount++;
            if (failureCount < MAX_FAILURES_PER_STAGE) {
                return;
            }

            failureCount = 0;
            if (stage == 0) {
                stage = 1;
                blockedUntil = now.plus(FIRST_LOCK);
                return;
            }
            stage = 2;
            blockedUntil = now.plus(SECOND_LOCK);
        }
    }
}
