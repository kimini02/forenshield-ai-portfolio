package com.example.demo.service.evidence.hls;

import com.example.demo.config.JwtProperties;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * HLS 재생용 opaque stream token.
 * Redis 키: STREAM:{token} → {@code userId:evidenceId} (StepUpTokenRedisService와 동일 패턴).
 * 로컬·테스트: Redis 미기동 시 인메모리 폴백. 운영: ElastiCache (application-prod.yaml spring.data.redis).
 */
@Service
public class EvidenceStreamTokenRedisService {

    private static final String KEY_PREFIX = "STREAM:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final Map<String, String> memoryStore = new ConcurrentHashMap<>();

    @Autowired
    public EvidenceStreamTokenRedisService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            JwtProperties jwtProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public String issueToken(Long userId, Long evidenceId) {
        String token = UUID.randomUUID().toString();
        String redisKey = key(token);
        String value = userId + ":" + evidenceId;
        long ttlMinutes = jwtProperties.resolveStepUpExpirationMinutes();

        if (useRedis()) {
            try {
                redisTemplate.opsForValue().set(redisKey, value, ttlMinutes, TimeUnit.MINUTES);
                return token;
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리 폴백
            }
        }

        memoryStore.put(redisKey, value);
        return token;
    }

    public Optional<StreamTokenContext> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String redisKey = key(token);
        String raw = null;
        if (useRedis()) {
            try {
                raw = redisTemplate.opsForValue().get(redisKey);
            } catch (RuntimeException ignored) {
                // 인메모리 조회로 폴백
            }
        }
        if (raw == null) {
            raw = memoryStore.get(redisKey);
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) {
            return Optional.empty();
        }
        try {
            Long userId = Long.parseLong(raw.substring(0, separator));
            Long evidenceId = Long.parseLong(raw.substring(separator + 1));
            return Optional.of(new StreamTokenContext(userId, evidenceId));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public long resolveExpiresInSeconds() {
        return jwtProperties.resolveStepUpExpirationMinutes() * 60L;
    }

    private boolean useRedis() {
        return redisTemplate != null;
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }

    public record StreamTokenContext(Long userId, Long evidenceId) {
    }
}
