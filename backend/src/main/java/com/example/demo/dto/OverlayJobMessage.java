package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OverlayJobMessage {

    private String jobType;
    private Long overlayJobId;
    private Long analysisRequestId;
    private Long evidenceId;
    private String module;
    private String filePath;
    private String s3ObjectKey;
    private String s3Bucket;
    private String s3Region;
    private String presignedDownloadUrl;
    private List<FrameRiskItem> frameRisks;
    private List<ClipRiskItem> clipRisks;
    private List<PairRiskItem> pairRisks;
    private String requestedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FrameRiskItem {
        private Integer frameIndex;
        private Double timestampSec;
        private Double riskScore;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClipRiskItem {
        private Integer clipIndex;
        private Integer startFrameIndex;
        private Integer endFrameIndex;
        private Double startTimeSec;
        private Double endTimeSec;
        private Double riskScore;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PairRiskItem {
        private Integer pairIndex;
        private Integer frameIndexA;
        private Integer frameIndexB;
        private Double timestampSec;
        private Double riskScore;
        private Double motionMagnitude;
    }
}
