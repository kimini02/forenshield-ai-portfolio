package com.example.demo.repository;

public interface DashboardStatsProjection {

    long getTotalAnalysisCount();

    long getDeepfakeDetectedCount();

    long getCompletedCount();

    long getInProgressCount();
}
