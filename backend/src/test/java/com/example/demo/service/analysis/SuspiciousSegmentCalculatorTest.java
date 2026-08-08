package com.example.demo.service.analysis;

import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuspiciousSegmentCalculatorTest {

    private final SuspiciousSegmentCalculator calculator = new SuspiciousSegmentCalculator();

    @Test
    @DisplayName("SK-675: 고위험 프레임을 startTime/endTime 구간으로 병합한다")
    void compute_mergesHighRiskFrames() {
        List<FrameRiskDto> frameRisks = List.of(
                frame(0, 0.0, 0.2),
                frame(1, 1.0, 0.75),
                frame(2, 2.0, 0.82),
                frame(3, 3.0, 0.79),
                frame(4, 4.0, 0.3)
        );

        List<SuspiciousSegmentDto> segments = calculator.compute(frameRisks, 0.70, 0.5);

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).getStartTime()).isEqualTo(1.0);
        assertThat(segments.get(0).getEndTime()).isEqualTo(3.0);
        assertThat(segments.get(0).getMaxRiskScore()).isEqualTo(0.82);
    }

    private FrameRiskDto frame(int index, double timestamp, double score) {
        return FrameRiskDto.builder()
                .frameIndex(index)
                .timestampSec(timestamp)
                .riskScore(score)
                .build();
    }
}
