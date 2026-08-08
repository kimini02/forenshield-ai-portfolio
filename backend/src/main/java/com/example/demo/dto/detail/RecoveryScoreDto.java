package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecoveryScoreDto {

    /** RQ-DTL-071: 0(심각)~100(양호) */
    private int recoveryScore;

    /** RQ-DTL-072: 데이터 소실도 0~100% */
    private int dataLossPercent;

    /** HIGH | MEDIUM | LOW | CRITICAL */
    private String grade;

    /** 점수 산출 근거 코드 (예: METADATA_EXTRACTION_FAILED) */
    private List<String> factors;
}
