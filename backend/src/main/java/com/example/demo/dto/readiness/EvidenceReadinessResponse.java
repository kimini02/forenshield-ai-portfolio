package com.example.demo.dto.readiness;

import com.example.demo.domain.enums.ReadinessSource;
import com.example.demo.domain.enums.ReadinessTier;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// API 응답(밖으로 나가는 것)
// GET/POST.../readiness, 업로드 응답의 readiness 가 사용함
// 프론트에 돌려주는 최종 JSON 형태
@Getter
@Builder
// 영상 분석 적합성(Analysis Readiness) 프로파일러.
public class EvidenceReadinessResponse {

    private Long evidenceId;
    private ReadinessSource source;
    private LocalDateTime checkedAt;
    private ReadinessTier readinessTier;
    private int confidenceCap;
    private List<String> reasons;
    private boolean requiresAcknowledgement;
    private String thresholdsVersion;
    private ReadinessVideoMetadataDto videoMetadata;
    private ReadinessFrameMetricsDto frameMetrics;
    private ReadinessSpatialDto spatial;
    private String frameCheckStatus;
    private String frameCheckMessage;
}
