package com.example.demo.dto.readiness;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReadinessSpatialDto {

    private String worstRegion; // 손실 최악 구역 
    private Double worstRegionScore; // 그 구역 손실 점수
    private Boolean spatiallyUniform; // 공간 균일성 (전체가 고른지 여부)
}


