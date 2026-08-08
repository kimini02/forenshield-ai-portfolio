package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminWeeklyAnalysisPoint {

    private String label;
    private String date;
    private long requestedCount;
    private long completedCount;
}
