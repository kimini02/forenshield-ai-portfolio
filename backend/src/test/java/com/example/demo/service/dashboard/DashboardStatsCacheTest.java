package com.example.demo.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.EvidenceStatsResponse;
import org.junit.jupiter.api.Test;

class DashboardStatsCacheTest {

    private final DashboardStatsCache cache = new DashboardStatsCache();

    @Test
    void putMakesValueAvailableForSameUploader() {
        EvidenceStatsResponse value = response(10L);

        cache.put(1L, value);

        assertThat(cache.get(1L)).isSameAs(value);
        assertThat(cache.get(2L)).isNull();
    }

    @Test
    void invalidateRemovesOnlyRequestedUploader() {
        EvidenceStatsResponse first = response(10L);
        EvidenceStatsResponse second = response(20L);
        cache.put(1L, first);
        cache.put(2L, second);

        cache.invalidate(1L);

        assertThat(cache.get(1L)).isNull();
        assertThat(cache.get(2L)).isSameAs(second);
    }

    @Test
    void nullInvalidateClearsAllEntries() {
        cache.put(1L, response(10L));
        cache.put(2L, response(20L));

        cache.invalidate(null);

        assertThat(cache.get(1L)).isNull();
        assertThat(cache.get(2L)).isNull();
    }

    private EvidenceStatsResponse response(long total) {
        return EvidenceStatsResponse.builder()
                .totalAnalysisCount(total)
                .deepfakeDetectedCount(total / 2)
                .completedCount(total / 4)
                .inProgressCount(total / 5)
                .build();
    }
}
