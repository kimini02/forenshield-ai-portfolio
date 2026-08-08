package com.example.demo.util;

import com.example.demo.domain.enums.AnalysisStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisStatusMapperTest {

    @Test
    void mapsQueueStatusForMonitoring() {
        assertThat(AnalysisStatusMapper.toQueueStatus(AnalysisStatus.QUEUED)).isEqualTo("WAITING");
        assertThat(AnalysisStatusMapper.toQueueStatus(AnalysisStatus.ANALYZING)).isEqualTo("ANALYZING");
        assertThat(AnalysisStatusMapper.toApiStatus(AnalysisStatus.QUEUED)).isEqualTo("PENDING");
        assertThat(AnalysisStatusMapper.toApiStatus(AnalysisStatus.ANALYZING)).isEqualTo("PROCESSING");
    }
}
