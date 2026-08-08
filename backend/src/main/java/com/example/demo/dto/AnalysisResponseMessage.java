package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResponseMessage {

    private Long analysisRequestId;
    private Long evidenceId;
    private String status;
    /** Mid-run progress 0~99 from AI IN_PROGRESS; optional on terminal messages. */
    private Integer progressPercent;
    private Double riskScore;
    private Double confidenceScore;
    private String riskLevel;
    private List<String> analysisReasons;
    private List<AnalysisVideoResultItem> results;
    private String analyzedAt;
    private String errorCode;
    private String message;
    private String modelName;
    private String modelVersion;
    private List<ModelScoreItem> modelScores;
    private List<String> evidence;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelScoreItem {
        private String moduleName;
        private Boolean detected;
        private Double score;
        private String modelName;
        private String modelVersion;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalysisVideoResultItem {

        private String type;
        private Boolean lipSyncDetected;
        private Double lipSyncScore;
        private Boolean frameEditDetected;
        private Double frameEditScore;
        private Boolean deepfakeDetected;
        private Double deepfakeScore;
        private Boolean splicingDetected;
        private Double splicingScore;
        private Boolean reEncodingDetected;
        private Double reEncodingScore;
        private List<FrameRiskItem> frameRisks;
        private List<ClipRiskItem> clipRisks;
        private List<PairRiskItem> pairRisks;
        private List<SuspiciousSegmentItem> suspiciousSegments;
        private List<SuspiciousSegmentItem> temporalSuspiciousSegments;
        private List<SuspiciousSegmentItem> opticalSuspiciousSegments;
        private List<ModuleTimelineItem> moduleTimelines;
        private String modelName;
        private String modelVersion;
        private List<ModelScoreItem> modelScores;
        private List<String> evidence;
        private List<RepresentativeFrameItem> representativeFrames;
        private String overlayVideoUrl;
        private List<ModelOverlayArtifactItem> modelOverlayArtifacts;

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

        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SuspiciousSegmentItem {
            private Double startTime;
            private Double endTime;
            private Double maxRiskScore;
            private String reason;
        }

        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ModuleTimelineItem {
            private String module;
            private String modelName;
            private String modelVersion;
            private Double videoScore;
            private Double threshold;
            private Boolean detected;
            private List<FrameRiskItem> frameRisks;
            private List<ClipRiskItem> clipRisks;
            private List<PairRiskItem> pairRisks;
            private List<SuspiciousSegmentItem> suspiciousSegments;
            private String overlayVideoUrl;
        }

        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ModelOverlayArtifactItem {
            private String key;
            private String category;
            private String label;
            private String overlayVideoUrl;
            private String status;
            private String description;
        }

        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RepresentativeFrameItem {
            private Double timeSec;
            private String timestamp;
            private Integer frameNumber;
            private Double score;
            private String imageUrl;
        }
    }
}
