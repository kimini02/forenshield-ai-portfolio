package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SuspiciousSegmentDto {

    private double startTime;
    private double endTime;
    private double maxRiskScore;
    private String reason;
}
