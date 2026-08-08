package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PairRiskDto {

    private int pairIndex;
    private int frameIndexA;
    private int frameIndexB;
    private double timestampSec;
    private double riskScore;
    private Double motionMagnitude;
}
