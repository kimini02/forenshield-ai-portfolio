package com.example.demo.service.analysis;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.dto.ClipRiskDto;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.PairRiskDto;
import com.example.demo.dto.RepresentativeFrameDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import com.example.demo.dto.detail.ModuleTimelineDto;
import com.example.demo.dto.detail.ModelOverlayArtifactDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoModuleDetailsReader {

    private final ObjectMapper objectMapper;

    public VisualizationData readVisualization(List<AnalysisModuleResult> moduleResults) {
        List<FrameRiskDto> frameRisks = List.of();
        List<SuspiciousSegmentDto> suspiciousSegments = List.of();
        List<ClipRiskDto> clipRisks = List.of();
        List<PairRiskDto> pairRisks = List.of();
        List<SuspiciousSegmentDto> temporalSuspiciousSegments = List.of();
        List<SuspiciousSegmentDto> opticalSuspiciousSegments = List.of();
        List<ModuleTimelineDto> moduleTimelines = List.of();
        List<RepresentativeFrameDto> representativeFrames = List.of();
        String overlayVideoUrl = null;
        List<ModelOverlayArtifactDto> modelOverlayArtifacts = List.of();
        List<String> evidenceItems = List.of();

        for (AnalysisModuleResult module : moduleResults) {
            Map<String, Object> details = parse(module.getDetailsJson());
            if (frameRisks.isEmpty()) {
                frameRisks = readFrameRisks(details);
            }
            if (suspiciousSegments.isEmpty()) {
                suspiciousSegments = readSuspiciousSegments(details);
            }
            if (clipRisks.isEmpty()) {
                clipRisks = readClipRisks(details);
            }
            if (pairRisks.isEmpty()) {
                pairRisks = readPairRisks(details);
            }
            if (temporalSuspiciousSegments.isEmpty()) {
                temporalSuspiciousSegments = readSuspiciousSegments(details.get("temporalSuspiciousSegments"));
            }
            if (opticalSuspiciousSegments.isEmpty()) {
                opticalSuspiciousSegments = readSuspiciousSegments(details.get("opticalSuspiciousSegments"));
            }
            if (moduleTimelines.isEmpty()) {
                moduleTimelines = readModuleTimelines(details);
            }
            if (representativeFrames.isEmpty()) {
                representativeFrames = readRepresentativeFrames(details);
            }
            if (overlayVideoUrl == null) {
                overlayVideoUrl = asString(details.get("overlayVideoUrl"));
            }
            if (modelOverlayArtifacts.isEmpty()) {
                modelOverlayArtifacts = readModelOverlayArtifacts(details);
            }
            if (evidenceItems.isEmpty()) {
                evidenceItems = readEvidenceItems(details);
            }
        }

        if (evidenceItems.isEmpty()) {
            evidenceItems = moduleResults.stream()
                    .map(AnalysisModuleResult::getEvidenceText)
                    .filter(text -> text != null && !text.isBlank())
                    .distinct()
                    .toList();
        }

        return new VisualizationData(
                frameRisks,
                suspiciousSegments,
                clipRisks,
                pairRisks,
                temporalSuspiciousSegments,
                opticalSuspiciousSegments,
                moduleTimelines,
                representativeFrames,
                overlayVideoUrl,
                modelOverlayArtifacts,
                evidenceItems
        );
    }

    public record VisualizationData(
            List<FrameRiskDto> frameRisks,
            List<SuspiciousSegmentDto> suspiciousSegments,
            List<ClipRiskDto> clipRisks,
            List<PairRiskDto> pairRisks,
            List<SuspiciousSegmentDto> temporalSuspiciousSegments,
            List<SuspiciousSegmentDto> opticalSuspiciousSegments,
            List<ModuleTimelineDto> moduleTimelines,
            List<RepresentativeFrameDto> representativeFrames,
            String overlayVideoUrl,
            List<ModelOverlayArtifactDto> modelOverlayArtifacts,
            List<String> evidenceItems
    ) {
    }

    public Map<String, Object> parse(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(detailsJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<FrameRiskDto> readFrameRisks(Map<String, Object> details) {
        return readFrameRisks(details.get("frameRisks"));
    }

    @SuppressWarnings("unchecked")
    public List<ClipRiskDto> readClipRisks(Map<String, Object> details) {
        Object raw = details.get("clipRisks");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return ClipRiskDto.builder()
                            .clipIndex(asInt(map.get("clipIndex")))
                            .startFrameIndex(asInt(map.get("startFrameIndex")))
                            .endFrameIndex(asInt(map.get("endFrameIndex")))
                            .startTimeSec(asDouble(map.get("startTimeSec")))
                            .endTimeSec(asDouble(map.get("endTimeSec")))
                            .riskScore(asDouble(map.get("riskScore")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<PairRiskDto> readPairRisks(Map<String, Object> details) {
        Object raw = details.get("pairRisks");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return PairRiskDto.builder()
                            .pairIndex(asInt(map.get("pairIndex")))
                            .frameIndexA(asInt(map.get("frameIndexA")))
                            .frameIndexB(asInt(map.get("frameIndexB")))
                            .timestampSec(asDouble(map.get("timestampSec")))
                            .riskScore(asDouble(map.get("riskScore")))
                            .motionMagnitude(asNullableDouble(map.get("motionMagnitude")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<ModuleTimelineDto> readModuleTimelines(Map<String, Object> details) {
        Object raw = details.get("moduleTimelines");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return ModuleTimelineDto.builder()
                            .module(asString(map.get("module")))
                            .modelName(asString(map.get("modelName")))
                            .modelVersion(asString(map.get("modelVersion")))
                            .videoScore(asDouble(map.get("videoScore")))
                            .threshold(asDouble(map.get("threshold")))
                            .detected(Boolean.TRUE.equals(map.get("detected")))
                            .frameRisks(readFrameRisks(map.get("frameRisks")))
                            .clipRisks(readClipRisksFromRaw(map.get("clipRisks")))
                            .pairRisks(readPairRisksFromRaw(map.get("pairRisks")))
                            .suspiciousSegments(readSuspiciousSegments(map.get("suspiciousSegments")))
                            .overlayVideoUrl(asString(map.get("overlayVideoUrl")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<ModelOverlayArtifactDto> readModelOverlayArtifacts(Map<String, Object> details) {
        Object raw = details.get("modelOverlayArtifacts");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return ModelOverlayArtifactDto.builder()
                            .key(asString(map.get("key")))
                            .category(asString(map.get("category")))
                            .label(asString(map.get("label")))
                            .overlayVideoUrl(asString(map.get("overlayVideoUrl")))
                            .status(asString(map.get("status")))
                            .description(asString(map.get("description")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<RepresentativeFrameDto> readRepresentativeFrames(Map<String, Object> details) {
        Object raw = details.get("representativeFrames");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return RepresentativeFrameDto.builder()
                            .timeSec(asNullableDouble(map.get("timeSec")))
                            .timestamp(asString(map.get("timestamp")))
                            .frameNumber(asNullableInt(map.get("frameNumber")))
                            .score(asNullableDouble(map.get("score")))
                            .imageUrl(asString(map.get("imageUrl")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<SuspiciousSegmentDto> readSuspiciousSegments(Map<String, Object> details) {
        return readSuspiciousSegments(details.get("suspiciousSegments"));
    }

    @SuppressWarnings("unchecked")
    private List<FrameRiskDto> readFrameRisks(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return FrameRiskDto.builder()
                            .frameIndex(asInt(map.get("frameIndex")))
                            .timestampSec(asDouble(map.get("timestampSec")))
                            .riskScore(asDouble(map.get("riskScore")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<ClipRiskDto> readClipRisksFromRaw(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return ClipRiskDto.builder()
                            .clipIndex(asInt(map.get("clipIndex")))
                            .startFrameIndex(asInt(map.get("startFrameIndex")))
                            .endFrameIndex(asInt(map.get("endFrameIndex")))
                            .startTimeSec(asDouble(map.get("startTimeSec")))
                            .endTimeSec(asDouble(map.get("endTimeSec")))
                            .riskScore(asDouble(map.get("riskScore")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<PairRiskDto> readPairRisksFromRaw(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return PairRiskDto.builder()
                            .pairIndex(asInt(map.get("pairIndex")))
                            .frameIndexA(asInt(map.get("frameIndexA")))
                            .frameIndexB(asInt(map.get("frameIndexB")))
                            .timestampSec(asDouble(map.get("timestampSec")))
                            .riskScore(asDouble(map.get("riskScore")))
                            .motionMagnitude(asNullableDouble(map.get("motionMagnitude")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<SuspiciousSegmentDto> readSuspiciousSegments(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return SuspiciousSegmentDto.builder()
                            .startTime(asDouble(map.get("startTime")))
                            .endTime(asDouble(map.get("endTime")))
                            .maxRiskScore(asDouble(map.get("maxRiskScore")))
                            .reason(asString(map.get("reason")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<String> readEvidenceItems(Map<String, Object> details) {
        Object raw = details.get("analysisReasons");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private Integer asNullableInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private Double asNullableDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
