package com.example.demo.dto.readiness;

import lombok.Builder;
import lombok.Getter;

// 프레임 품질 3종 

@Getter
@Builder
public class ReadinessFrameMetricsDto {

    private ReadinessMetricAggregateDto blur; // 선명도
    private ReadinessMetricAggregateDto blockiness; // 블록 경계 손실
    private ReadinessMetricAggregateDto fftPeak; // 고주파 격자 아티팩트
}
