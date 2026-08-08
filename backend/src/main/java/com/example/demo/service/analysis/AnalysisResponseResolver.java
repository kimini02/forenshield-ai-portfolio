package com.example.demo.service.analysis;

import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.dto.ClipRiskDto;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.PairRiskDto;
import com.example.demo.dto.RepresentativeFrameDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import com.example.demo.dto.VideoDeepfakeTimelineDto;
import com.example.demo.dto.detail.ModuleTimelineDto;
import com.example.demo.dto.detail.ModelOverlayArtifactDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalysisResponseResolver {

    static final String DEFAULT_MODEL_NAME = "forenshield-video-ai";
    static final String DEFAULT_MODEL_VERSION = "external";

    public AnalysisResponseMessage.AnalysisVideoResultItem findVideoResult(
            List<AnalysisResponseMessage.AnalysisVideoResultItem> results
    ) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.stream()
                .filter(item -> item.getType() == null || "video".equalsIgnoreCase(item.getType()))
                .findFirst()
                .orElse(results.get(0));
    }

    public List<String> resolveEvidenceItems(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getEvidence() != null && !videoResult.getEvidence().isEmpty()) {
            return videoResult.getEvidence();
        }
        if (response != null && response.getEvidence() != null && !response.getEvidence().isEmpty()) {
            return response.getEvidence();
        }
        if (response != null && response.getAnalysisReasons() != null && !response.getAnalysisReasons().isEmpty()) {
            return response.getAnalysisReasons();
        }
        return List.of();
    }

    public List<AnalysisResponseMessage.ModelScoreItem> resolveModelScores(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelScores() != null && !videoResult.getModelScores().isEmpty()) {
            return videoResult.getModelScores();
        }
        if (response != null && response.getModelScores() != null && !response.getModelScores().isEmpty()) {
            return response.getModelScores();
        }
        return List.of();
    }

    public String resolveModelName(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelName() != null && !videoResult.getModelName().isBlank()) {
            return videoResult.getModelName();
        }
        if (response != null && response.getModelName() != null && !response.getModelName().isBlank()) {
            return response.getModelName();
        }
        return DEFAULT_MODEL_NAME;
    }

    public String resolveModelVersion(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelVersion() != null && !videoResult.getModelVersion().isBlank()) {
            return videoResult.getModelVersion();
        }
        if (response != null && response.getModelVersion() != null && !response.getModelVersion().isBlank()) {
            return response.getModelVersion();
        }
        return DEFAULT_MODEL_VERSION;
    }

    public VideoDeepfakeTimelineDto toVideoDeepfakeTimeline(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult
    ) {
        if (videoResult == null) {
            return VideoDeepfakeTimelineDto.builder()
                    .frameRisks(List.of())
                    .suspiciousSegments(List.of())
                    .clipRisks(List.of())
                    .pairRisks(List.of())
                    .temporalSuspiciousSegments(List.of())
                    .opticalSuspiciousSegments(List.of())
                    .moduleTimelines(List.of())
                    .representativeFrames(List.of())
                    .modelOverlayArtifacts(List.of())
                    .build();
        }
        return VideoDeepfakeTimelineDto.builder()
                .frameRisks(toFrameRiskDtos(videoResult.getFrameRisks()))
                .suspiciousSegments(toSuspiciousSegmentDtos(videoResult.getSuspiciousSegments()))
                .clipRisks(toClipRiskDtos(videoResult.getClipRisks()))
                .pairRisks(toPairRiskDtos(videoResult.getPairRisks()))
                .temporalSuspiciousSegments(toSuspiciousSegmentDtos(videoResult.getTemporalSuspiciousSegments()))
                .opticalSuspiciousSegments(toSuspiciousSegmentDtos(videoResult.getOpticalSuspiciousSegments()))
                .moduleTimelines(toModuleTimelineDtos(videoResult.getModuleTimelines()))
                .representativeFrames(toRepresentativeFrameDtos(videoResult.getRepresentativeFrames()))
                .overlayVideoUrl(videoResult.getOverlayVideoUrl())
                .modelOverlayArtifacts(toModelOverlayArtifactDtos(videoResult.getModelOverlayArtifacts()))
                .build();
    }

    public List<FrameRiskDto> toFrameRiskDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem> frameRisks
    ) {
        if (frameRisks == null || frameRisks.isEmpty()) {
            return List.of();
        }
        List<FrameRiskDto> converted = new ArrayList<>();
        for (int i = 0; i < frameRisks.size(); i++) {
            AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem item = frameRisks.get(i);
            converted.add(FrameRiskDto.builder()
                    .frameIndex(item.getFrameIndex() == null ? i : item.getFrameIndex())
                    .timestampSec(defaultDouble(item.getTimestampSec()))
                    .riskScore(defaultDouble(item.getRiskScore()))
                    .build());
        }
        return converted;
    }

    public List<ClipRiskDto> toClipRiskDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.ClipRiskItem> clipRisks
    ) {
        if (clipRisks == null || clipRisks.isEmpty()) {
            return List.of();
        }
        List<ClipRiskDto> converted = new ArrayList<>();
        for (int i = 0; i < clipRisks.size(); i++) {
            AnalysisResponseMessage.AnalysisVideoResultItem.ClipRiskItem item = clipRisks.get(i);
            converted.add(ClipRiskDto.builder()
                    .clipIndex(item.getClipIndex() == null ? i : item.getClipIndex())
                    .startFrameIndex(defaultInt(item.getStartFrameIndex()))
                    .endFrameIndex(defaultInt(item.getEndFrameIndex()))
                    .startTimeSec(defaultDouble(item.getStartTimeSec()))
                    .endTimeSec(defaultDouble(item.getEndTimeSec()))
                    .riskScore(defaultDouble(item.getRiskScore()))
                    .build());
        }
        return converted;
    }

    public List<PairRiskDto> toPairRiskDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.PairRiskItem> pairRisks
    ) {
        if (pairRisks == null || pairRisks.isEmpty()) {
            return List.of();
        }
        List<PairRiskDto> converted = new ArrayList<>();
        for (int i = 0; i < pairRisks.size(); i++) {
            AnalysisResponseMessage.AnalysisVideoResultItem.PairRiskItem item = pairRisks.get(i);
            converted.add(PairRiskDto.builder()
                    .pairIndex(item.getPairIndex() == null ? i : item.getPairIndex())
                    .frameIndexA(defaultInt(item.getFrameIndexA()))
                    .frameIndexB(defaultInt(item.getFrameIndexB()))
                    .timestampSec(defaultDouble(item.getTimestampSec()))
                    .riskScore(defaultDouble(item.getRiskScore()))
                    .motionMagnitude(item.getMotionMagnitude())
                    .build());
        }
        return converted;
    }

    public List<SuspiciousSegmentDto> toSuspiciousSegmentDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem> segments
    ) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return segments.stream()
                .map(item -> SuspiciousSegmentDto.builder()
                        .startTime(defaultDouble(item.getStartTime()))
                        .endTime(defaultDouble(item.getEndTime()))
                        .maxRiskScore(defaultDouble(item.getMaxRiskScore()))
                        .reason(item.getReason())
                        .build())
                .toList();
    }

    public List<ModuleTimelineDto> toModuleTimelineDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.ModuleTimelineItem> timelines
    ) {
        if (timelines == null || timelines.isEmpty()) {
            return List.of();
        }
        return timelines.stream()
                .map(item -> ModuleTimelineDto.builder()
                        .module(item.getModule())
                        .modelName(item.getModelName())
                        .modelVersion(item.getModelVersion())
                        .videoScore(defaultDouble(item.getVideoScore()))
                        .threshold(defaultDouble(item.getThreshold()))
                        .detected(Boolean.TRUE.equals(item.getDetected()))
                        .frameRisks(toFrameRiskDtos(item.getFrameRisks()))
                        .clipRisks(toClipRiskDtos(item.getClipRisks()))
                        .pairRisks(toPairRiskDtos(item.getPairRisks()))
                        .suspiciousSegments(toSuspiciousSegmentDtos(item.getSuspiciousSegments()))
                        .overlayVideoUrl(item.getOverlayVideoUrl())
                        .build())
                .toList();
    }

    public List<ModelOverlayArtifactDto> toModelOverlayArtifactDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.ModelOverlayArtifactItem> artifacts
    ) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        return artifacts.stream()
                .map(item -> ModelOverlayArtifactDto.builder()
                        .key(item.getKey())
                        .category(item.getCategory())
                        .label(item.getLabel())
                        .overlayVideoUrl(item.getOverlayVideoUrl())
                        .status(item.getStatus())
                        .description(item.getDescription())
                        .build())
                .toList();
    }

    public List<RepresentativeFrameDto> toRepresentativeFrameDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.RepresentativeFrameItem> representativeFrames
    ) {
        if (representativeFrames == null || representativeFrames.isEmpty()) {
            return List.of();
        }
        return representativeFrames.stream()
                .map(item -> RepresentativeFrameDto.builder()
                        .timeSec(item.getTimeSec())
                        .timestamp(item.getTimestamp())
                        .frameNumber(item.getFrameNumber())
                        .score(item.getScore())
                        .imageUrl(item.getImageUrl())
                        .build())
                .toList();
    }

    public AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem toFrameRiskItem(FrameRiskDto frameRisk) {
        return AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem.builder()
                .frameIndex(frameRisk.getFrameIndex())
                .timestampSec(frameRisk.getTimestampSec())
                .riskScore(frameRisk.getRiskScore())
                .build();
    }

    public AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem toSuspiciousSegmentItem(
            SuspiciousSegmentDto segment
    ) {
        return AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem.builder()
                .startTime(segment.getStartTime())
                .endTime(segment.getEndTime())
                .maxRiskScore(segment.getMaxRiskScore())
                .reason(segment.getReason())
                .build();
    }

    public double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
