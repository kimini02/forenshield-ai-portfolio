package com.example.demo.security;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0);
    }

    public static RateLimitDecision blocked(long retryAfterSeconds) {
        return new RateLimitDecision(false, retryAfterSeconds);
    }
}
