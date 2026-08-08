package com.example.demo.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminAnalysisStatsResponse {

    private long weeklyTotalCount;
    private double deepfakeDetectionRate;
    private double averageAnalysisMinutes;
    private List<AdminWeeklyAnalysisPoint> weeklyPoints;
    private AdminRiskDistribution riskDistribution;
}
