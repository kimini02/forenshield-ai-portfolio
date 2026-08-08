package com.example.demo.dto.readiness;

import lombok.Builder;
import lombok.Getter;

// 지표 집계 (mean/min/max)
// blur, blockiness, fft 각각에 쓰는 공통 구조

@Getter
@Builder
public class ReadinessMetricAggregateDto {

    private Double mean;
    private Double min;
    private Double max;
}
