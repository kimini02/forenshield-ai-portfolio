package com.example.demo.service.analysis;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.dto.detail.ModelScoreDto;
import com.example.demo.dto.detail.ModuleResultDto;
import com.example.demo.util.ApiDateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

final class AnalysisDetailFormatters {

    private AnalysisDetailFormatters() {
    }

    static String formatUtc(LocalDateTime value) {
        return ApiDateTimeFormatter.formatUtc(value);
    }

    static ModuleResultDto toModuleResult(AnalysisModuleResult moduleResult) {
        return ModuleResultDto.builder()
                .moduleName(moduleResult.getModuleName())
                .detected(Boolean.TRUE.equals(moduleResult.getDetected()))
                .score(moduleResult.getScore() != null ? moduleResult.getScore() : 0.0)
                .confidence(moduleResult.getConfidence())
                .modelName(moduleResult.getModelName())
                .modelVersion(moduleResult.getModelVersion())
                .details(moduleResult.getDetailsJson() != null ? moduleResult.getDetailsJson() : "{}")
                .build();
    }

    static List<ModelScoreDto> toModelScores(List<AnalysisModuleResult> moduleResults) {
        return moduleResults.stream()
                .filter(module -> !VideoAnalysisDetailsBuilder.MODULE_VIDEO_TIMELINE.equals(module.getModuleName()))
                .map(module -> ModelScoreDto.builder()
                        .moduleName(module.getModuleName())
                        .detected(Boolean.TRUE.equals(module.getDetected()))
                        .score(module.getScore() != null ? module.getScore() : 0.0)
                        .modelName(module.getModelName())
                        .modelVersion(module.getModelVersion())
                        .build())
                .toList();
    }
}
