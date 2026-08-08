package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisStatusResponse {

    private Long evidenceId;
    private Long analysisRequestId;
    private String status;
    /** SK-923: WAITING · ANALYZING · COMPLETED · FAILED */
    private String queueStatus;
    private int progressPercent;
    /** status=FAILED 일 때만. 예: ANALYSIS_FAILED, RABBITMQ_PUBLISH_FAILED */
    private String errorCode;
    /** status=FAILED 일 때 사용자/FE 표시용 요약 (시크릿 포함 금지) */
    private String errorMessage;
    /** WBS 3.1.4: QUEUED 상태일 때 대기열 순번 (1부터) */
    private Integer queuePosition;
    /** WBS 3.1.4: 현재 대기 중인 전체 분석 건수 */
    private Integer queueDepth;
}
