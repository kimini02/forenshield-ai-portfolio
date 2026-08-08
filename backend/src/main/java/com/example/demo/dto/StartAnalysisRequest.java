package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartAnalysisRequest {

    /** 기능명세서 표준: 단건 분석 요청 */
    private Long evidenceId;

    /** 레거시: 복수 증거 일괄 분석 */
    private List<Long> evidenceIds;

    /** 사건명 (미입력 시 업로드 시 저장된 사건명 사용) */
    private String caseName;

    /**
     * 화질 적합성 안내(POOR/CAUTION)를 확인하고 분석을 계속 진행함을 표시한다.
     * requiresAcknowledgement=true 인 증거가 있을 때 true 가 아니면 409 QUALITY_WARNING_REQUIRED.
     */
    private Boolean acknowledgeQualityWarning;
}
