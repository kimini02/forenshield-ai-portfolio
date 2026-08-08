package com.example.demo.dto.mypage;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisHistoryItemResponse {

    private Long evidenceId;
    private Long analysisRequestId;
    private String caseId;
    private String caseName;
    private String fileName;
    private String requestedAt;
    private String completedAt;
    /** PENDING · PROCESSING · COMPLETED · FAILED */
    private String status;
    /** WAITING · ANALYZING · COMPLETED · FAILED (SK-923) */
    private String queueStatus;
    private String riskLevel;
    private Double riskScore;
    private boolean completed;
}
