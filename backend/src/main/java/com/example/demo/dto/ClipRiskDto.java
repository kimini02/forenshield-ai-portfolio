package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClipRiskDto {

    private int clipIndex;
    private int startFrameIndex;
    private int endFrameIndex;
    private double startTimeSec;
    private double endTimeSec;
    private double riskScore;
}
