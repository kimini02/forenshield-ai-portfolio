package com.example.demo.service.dashboard;

import com.example.demo.dto.EvidenceStatsResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DashboardStatsCache {

    private static final long TTL_MILLIS = 30_000L;

    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public EvidenceStatsResponse get(Long uploaderId) {
        CacheEntry entry = cache.get(uploaderId);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value();
    }

    public void put(Long uploaderId, EvidenceStatsResponse value) {
        cache.put(uploaderId, new CacheEntry(value, Instant.now().toEpochMilli() + TTL_MILLIS));
    }

    public void invalidate(Long uploaderId) {
        if (uploaderId == null) {
            cache.clear();
            return;
        }
        cache.remove(uploaderId);
    }

    private record CacheEntry(EvidenceStatsResponse value, long expiresAtMillis) {
        boolean isExpired() {
            return Instant.now().toEpochMilli() >= expiresAtMillis;
        }
    }
}
