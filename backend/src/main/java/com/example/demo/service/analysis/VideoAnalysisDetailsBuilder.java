package com.example.demo.service.analysis;

import com.example.demo.config.VideoFrameAnalysisProperties;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.dto.VideoDeepfakeTimelineDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoAnalysisDetailsBuilder {

    public static final String MODULE_VIDEO_TIMELINE = "video_timeline";

    private final VideoFrameAnalysisProperties frameAnalysisProperties;
    private final AnalysisResponseResolver responseResolver;

    public Map<String, Object> buildTimelineDetails(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            VideoDeepfakeTimelineDto timeline,
            List<String> evidenceItems
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", "video");
        putDetection(details, "lipSync", videoResult.getLipSyncDetected(), videoResult.getLipSyncScore());
        putDetection(details, "frameEdit", videoResult.getFrameEditDetected(), videoResult.getFrameEditScore());
        putDetection(details, "deepfake", videoResult.getDeepfakeDetected(), videoResult.getDeepfakeScore());
        putDetection(details, "splicing", videoResult.getSplicingDetected(), videoResult.getSplicingScore());
        details.put("videoEditDetected", Boolean.TRUE.equals(videoResult.getSplicingDetected()));
        details.put("videoEditScore", responseResolver.defaultDouble(videoResult.getSplicingScore()));
        putDetection(details, "reEncoding", videoResult.getReEncodingDetected(), videoResult.getReEncodingScore());
        details.put("highRiskFrameScoreThreshold", frameAnalysisProperties.getHighRiskFrameScoreThreshold());
        details.put("frameRisks", timeline.getFrameRisks() == null ? List.of() : timeline.getFrameRisks());
        details.put("suspiciousSegments", timeline.getSuspiciousSegments() == null ? List.of() : timeline.getSuspiciousSegments());
        details.put("clipRisks", timeline.getClipRisks() == null ? List.of() : timeline.getClipRisks());
        details.put("pairRisks", timeline.getPairRisks() == null ? List.of() : timeline.getPairRisks());
        details.put(
                "temporalSuspiciousSegments",
                timeline.getTemporalSuspiciousSegments() == null ? List.of() : timeline.getTemporalSuspiciousSegments()
        );
        details.put(
                "opticalSuspiciousSegments",
                timeline.getOpticalSuspiciousSegments() == null ? List.of() : timeline.getOpticalSuspiciousSegments()
        );
        details.put("moduleTimelines", timeline.getModuleTimelines() == null ? List.of() : timeline.getModuleTimelines());
        details.put(
                "representativeFrames",
                timeline.getRepresentativeFrames() == null ? List.of() : timeline.getRepresentativeFrames()
        );
        details.put("overlayVideoUrl", timeline.getOverlayVideoUrl());
        details.put(
                "modelOverlayArtifacts",
                timeline.getModelOverlayArtifacts() == null ? List.of() : timeline.getModelOverlayArtifacts()
        );
        details.put("analysisReasons", evidenceItems == null ? List.of() : evidenceItems);
        return details;
    }

    private void putDetection(Map<String, Object> details, String prefix, Boolean detected, Double score) {
        details.put(prefix + "Detected", Boolean.TRUE.equals(detected));
        details.put(prefix + "Score", responseResolver.defaultDouble(score));
    }
}
