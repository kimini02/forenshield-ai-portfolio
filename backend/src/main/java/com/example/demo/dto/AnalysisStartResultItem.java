package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisStartResultItem {

    private Long evidenceId;
    private Long analysisRequestId;
    /** RabbitMQ 큐 등록 성공 여부 (SK-921) */
    private boolean queueRegistered;
    private String status;
    private String queueStatus;
    private String errorCode;
    private String errorMessage;
}
