package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceStatsResponse {

    /** RQ-DSH-043: 총 분석 건수 */
    private long totalAnalysisCount;

    /** RQ-DSH-043: 딥페이크·변조 의심 탐지 건수 (HIGH/MEDIUM) */
    private long deepfakeDetectedCount;

    /** RQ-DSH-043: 분석 완료 건수 */
    private long completedCount;

    /** RQ-DSH-043: 처리 중 건수 (QUEUED, ANALYZING) */
    private long inProgressCount;
}
