package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisTrendPoint {

    /** ISO-8601 calendar date (yyyy-MM-dd) */
    private String date;

    /** RQ-DSH-044: 해당 일자에 완료(COMPLETED)된 분석 건수 */
    private long completedCount;
}
