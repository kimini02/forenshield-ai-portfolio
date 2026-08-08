package com.example.demo.service.auth;

import com.example.demo.config.AuthRefreshProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// 리프레시 JWT를 Redis(ElastiCache)에 사용자 ID 단위로 저장·검증·삭제한다.
// AuthService(로그인·재발급·로그아웃)에서 호출. Redis 미연결 시 인메모리로 폴백(로컬·테스트용).
@Service
public class RefreshTokenRedisService {

    // Redis 키 형식: RT:{userId}
    private static final String KEY_PREFIX = "RT:";

    private final StringRedisTemplate redisTemplate;
    private final AuthRefreshProperties authRefreshProperties;

    // 로컬·테스트용 — 운영(EKS 2 Pod)에서는 Redis가 정상이어야 재발급이 Pod 간 공유된다
    private final Map<String, MemoryEntry> memoryStore = new ConcurrentHashMap<>();

    @Autowired
    public RefreshTokenRedisService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            AuthRefreshProperties authRefreshProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.authRefreshProperties = authRefreshProperties;
    }

    // 로그인·재발급 성공 시 리프레시 JWT 저장 (idle TTL 갱신)
    public void saveRefreshToken(Long userId, String refreshToken) {
        String redisKey = key(userId);
        long idleMinutes = idleTimeoutMinutes();
        if (useRedis()) {
            try {
                redisTemplate.opsForValue().set(
                        redisKey,
                        refreshToken,
                        idleMinutes,
                        TimeUnit.MINUTES
                );
                return;
            } catch (RuntimeException ignored) {
                // Redis 미기동(로컬·테스트) 시 인메모리로 폴백
            }
        }
        memoryStore.put(redisKey, new MemoryEntry(refreshToken, expiresAt(idleMinutes)));
    }

    // 재발급 시 Redis 저장값 조회
    public String getRefreshToken(Long userId) {
        String redisKey = key(userId);
        if (useRedis()) {
            try {
                return redisTemplate.opsForValue().get(redisKey);
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리 조회
            }
        }
        return getValidMemoryToken(redisKey);
    }

    // 로그아웃·비밀번호 변경·계정 정지 시 리프레시 무효화
    public void deleteRefreshToken(Long userId) {
        String redisKey = key(userId);
        if (useRedis()) {
            try {
                redisTemplate.delete(redisKey);
            } catch (RuntimeException ignored) {
                // Redis 미기동 시 인메모리만 삭제
            }
        }
        memoryStore.remove(redisKey);
    }

    // 재발급 API에서 쿠키 JWT와 Redis 저장값 일치 여부 확인
    public boolean matchesRefreshToken(Long userId, String refreshToken) {
        String saved = getRefreshToken(userId);
        return saved != null && saved.equals(refreshToken);
    }

    private boolean useRedis() {
        return redisTemplate != null;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private long idleTimeoutMinutes() {
        int minutes = authRefreshProperties.getIdleTimeoutMinutes();
        return minutes > 0 ? minutes : 20;
    }

    private long expiresAt(long idleMinutes) {
        return System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(idleMinutes);
    }

    private String getValidMemoryToken(String redisKey) {
        MemoryEntry entry = memoryStore.get(redisKey);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMs() <= System.currentTimeMillis()) {
            memoryStore.remove(redisKey);
            return null;
        }
        return entry.token();
    }

    private record MemoryEntry(String token, long expiresAtEpochMs) {
    }
}
