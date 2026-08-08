package com.example.demo.dto.readiness;

import com.example.demo.domain.enums.ReadinessSource;
import com.example.demo.domain.enums.ReadinessTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

// DB 저장용
// EvidenceMetadata.readinessJson 에 serialize/deserialize 용.
// EvidenceReadinessResponse와 거의 같지만 evidenceId 없음 
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
// evidence_metadata.readiness_json 직렬화 스키마.
public class ReadinessSnapshot {

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
