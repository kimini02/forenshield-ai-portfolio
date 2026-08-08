package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentAnalysisItem {

    private Long evidenceId;
    private String caseId;
    private String caseName;
    private Long analysisRequestId;
    private String fileName;
    /** ISO-8601 UTC (yyyy-MM-dd'T'HH:mm:ss'Z') */
    private String requestedAt;
    /** PENDING · PROCESSING · COMPLETED · FAILED */
    private String status;
    /** COMPLETED 시 위험 지수(0~100). 그 외 null */
    private Double riskScore;
    /** COMPLETED 시 LOW · MEDIUM · HIGH. 그 외 null */
    private String riskLevel;
    /** COMPLETED 시 NORMAL(초록) · SUSPICIOUS(주황) · DANGER(빨강). 그 외 null */
    private String verdictIndicator;
}
