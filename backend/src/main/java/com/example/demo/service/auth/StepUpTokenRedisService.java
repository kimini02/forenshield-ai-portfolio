package com.example.demo.service.auth;

import com.example.demo.config.JwtProperties;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Step-up 재인증 opaque 토큰 저장.
 * Redis 키: STEPUP:{token} → userId (RefreshTokenRedisService와 동일 패턴, Redis + 인메모리 폴백).
 */
@Service
public class StepUpTokenRedisService {

    private static final String KEY_PREFIX = "STEPUP:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final Map<String, MemoryEntry> memoryStore = new ConcurrentHashMap<>();

    @Autowired
    public StepUpTokenRedisService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            JwtProperties jwtProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        String redisKey = key(token);
        String userIdValue = String.valueOf(userId);
        long ttlMinutes = jwtProperties.resolveStepUpExpirationMinutes();

        if (useRedis()) {
            try {
                redisTemplate.opsForValue().set(redisKey, userIdValue, ttlMinutes, TimeUnit.MINUTES);
                return token;
            } catch (RuntimeException ignored) {
                // Redis 미기동(로컬·테스트) 시 인메모리로 폴백
            }
        }

        long expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttlMinutes);
        memoryStore.put(redisKey, new MemoryEntry(userId, expiresAtEpochMs));
        return token;
    }

    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String redisKey = key(token);
        if (useRedis()) {
            try {
                Long remainingMs = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
                if (remainingMs == null || remainingMs <= 0) {
                    if (remainingMs != null) {
                        redisTemplate.delete(redisKey);
                    }
                } else {
                    String userId = redisTemplate.opsForValue().get(redisKey);
                    if (userId != null) {
                        return Long.parseLong(userId);
                    }
                }
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리 조회
            }
        }

        MemoryEntry entry = memoryStore.get(redisKey);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMs() <= System.currentTimeMillis()) {
            memoryStore.remove(redisKey);
            return null;
        }
        return entry.userId();
    }

    /**
     * @return 연장 후 남은 유효 시간(ms). 토큰이 없거나 만료됐으면 null.
     */
    public Long extendToken(String token, long extensionMs) {
        if (token == null || token.isBlank() || extensionMs <= 0) {
            return null;
        }

        String redisKey = key(token);
        if (useRedis()) {
            try {
                String userId = redisTemplate.opsForValue().get(redisKey);
                if (userId == null) {
                    return null;
                }
                Long remainingMs = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
                if (remainingMs == null || remainingMs <= 0) {
                    redisTemplate.delete(redisKey);
                    return null;
                }
                long newTtlMs = remainingMs + extensionMs;
                long newTtlSeconds = Math.max(1L, (newTtlMs + 999) / 1000);
                Boolean extended = redisTemplate.expire(redisKey, newTtlSeconds, TimeUnit.SECONDS);
                if (!Boolean.TRUE.equals(extended)) {
                    return null;
                }
                return newTtlSeconds * 1000L;
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리 연장
            }
        }

        MemoryEntry entry = memoryStore.get(redisKey);
        if (entry == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long remainingMs = entry.expiresAtEpochMs() - now;
        if (remainingMs <= 0) {
            memoryStore.remove(redisKey);
            return null;
        }
        long newExpiresAt = entry.expiresAtEpochMs() + extensionMs;
        memoryStore.put(redisKey, new MemoryEntry(entry.userId(), newExpiresAt));
        return newExpiresAt - now;
    }

    /** @return 남은 유효 시간(ms). 없거나 만료됐으면 null. */
    public Long resolveRemainingMs(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String redisKey = key(token);
        if (useRedis()) {
            try {
                Long remainingMs = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
                if (remainingMs == null || remainingMs <= 0) {
                    return null;
                }
                return remainingMs;
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리 조회
            }
        }

        MemoryEntry entry = memoryStore.get(redisKey);
        if (entry == null) {
            return null;
        }
        long remainingMs = entry.expiresAtEpochMs() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            memoryStore.remove(redisKey);
            return null;
        }
        return remainingMs;
    }

    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String redisKey = key(token);
        if (useRedis()) {
            try {
                redisTemplate.delete(redisKey);
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리만 삭제
            }
        }
        memoryStore.remove(redisKey);
    }

    private boolean useRedis() {
        return redisTemplate != null;
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }

    private record MemoryEntry(long userId, long expiresAtEpochMs) {
    }
}
