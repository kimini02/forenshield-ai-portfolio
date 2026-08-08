package com.example.demo.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SignupRateLimitService {

    private static final Map<String, RateLimitRule> RULES = Map.of(
            "POST /api/v1/auth/signup", new RateLimitRule(5, Duration.ofMinutes(1), RateLimitScope.IP),
            "GET /api/v1/auth/username/check", new RateLimitRule(20, Duration.ofMinutes(1), RateLimitScope.IP),
            "POST /api/v1/invite-codes/validate", new RateLimitRule(10, Duration.ofMinutes(1), RateLimitScope.IP),
            "POST /api/v1/auth/step-up/verify", new RateLimitRule(5, Duration.ofMinutes(1), RateLimitScope.USER),
            "POST /api/v1/auth/step-up/extend", new RateLimitRule(10, Duration.ofMinutes(1), RateLimitScope.USER)
    );

    private final ConcurrentHashMap<String, RequestBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public RateLimitDecision check(String method, String path, String clientIp) {
        return check(method, path, clientIp, null);
    }

    public RateLimitDecision check(String method, String path, String clientIp, String userLoginId) {
        RateLimitRule rule = RULES.get(method + " " + path);
        if (rule == null) {
            return RateLimitDecision.allow();
        }

        String subjectKey = resolveSubjectKey(rule.scope(), clientIp, userLoginId);
        if (subjectKey == null) {
            return RateLimitDecision.allow();
        }

        Instant now = Instant.now();
        String key = method + " " + path + " " + subjectKey;
        AtomicReference<RequestBucket> updatedBucket = new AtomicReference<>();

        buckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.isExpired(now, rule.window())) {
                RequestBucket bucket = new RequestBucket(now, 1);
                updatedBucket.set(bucket);
                return bucket;
            }

            RequestBucket bucket = existing.increment();
            updatedBucket.set(bucket);
            return bucket;
        });

        if (requestCount.incrementAndGet() % 100 == 0) {
            cleanupExpiredBuckets(now);
        }

        RequestBucket bucket = Objects.requireNonNull(updatedBucket.get());
        if (bucket.count() <= rule.maxRequests()) {
            return RateLimitDecision.allow();
        }

        long retryAfterSeconds = Math.max(1, rule.window().minus(Duration.between(bucket.windowStart(), now)).toSeconds());
        return RateLimitDecision.blocked(retryAfterSeconds);
    }

    public void reset() {
        buckets.clear();
        requestCount.set(0);
    }

    private String resolveSubjectKey(RateLimitScope scope, String clientIp, String userLoginId) {
        return switch (scope) {
            case IP -> "ip:" + clientIp;
            case USER -> userLoginId == null || userLoginId.isBlank() ? null : "user:" + userLoginId;
        };
    }

    private void cleanupExpiredBuckets(Instant now) {
        buckets.entrySet().removeIf(entry -> {
            RateLimitRule rule = findRuleByKey(entry.getKey());
            return rule != null && entry.getValue().isExpired(now, rule.window());
        });
    }

    private RateLimitRule findRuleByKey(String bucketKey) {
        return RULES.entrySet()
                .stream()
                .filter(entry -> bucketKey.startsWith(entry.getKey() + " "))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private enum RateLimitScope {
        IP,
        USER
    }

    private record RateLimitRule(int maxRequests, Duration window, RateLimitScope scope) {
    }

    private record RequestBucket(Instant windowStart, int count) {

        boolean isExpired(Instant now, Duration window) {
            return !windowStart.plus(window).isAfter(now);
        }

        RequestBucket increment() {
            return new RequestBucket(windowStart, count + 1);
        }
    }
}
