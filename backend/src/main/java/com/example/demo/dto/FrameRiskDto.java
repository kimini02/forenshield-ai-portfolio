package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FrameRiskDto {

    private int frameIndex;
    private double timestampSec;
    private double riskScore;
}
