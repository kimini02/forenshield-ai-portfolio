package com.example.demo.service.analysis;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.dto.detail.AnalysisInfoDto;
import com.example.demo.util.AnalysisStatusMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisInfoAssembler {

    private final VideoModuleDetailsReader videoModuleDetailsReader;
    private final VisualizationArtifactUrlRefresher visualizationArtifactUrlRefresher;

    public AnalysisInfoDto assemble(
            AnalysisRequest request,
            AnalysisResult result,
            List<AnalysisModuleResult> moduleResults
    ) {
        if (request == null) {
            return emptyPending("아직 분석이 요청되지 않았습니다.");
        }

        String status = AnalysisStatusMapper.toApiStatus(request.getStatus());
        String queueStatus = AnalysisStatusMapper.toQueueStatus(request.getStatus());
        if (result == null) {
            AnalysisInfoDto.AnalysisInfoDtoBuilder builder = incompleteBuilder(request, status, queueStatus);
            if ("FAILED".equals(status)) {
                builder.errorCode(request.getErrorCode())
                        .errorMessage(request.getErrorMessage());
            }
            return builder.build();
        }

        return completedBuilder(request, result, moduleResults, status, queueStatus).build();
    }

    private AnalysisInfoDto emptyPending(String summary) {
        return AnalysisInfoDto.builder()
                .status("PENDING")
                .queueStatus("WAITING")
                .summary(summary)
                .completed(false)
                .moduleResults(List.of())
                .modelScores(List.of())
                .evidenceItems(List.of())
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

    private AnalysisInfoDto.AnalysisInfoDtoBuilder incompleteBuilder(
            AnalysisRequest request,
            String status,
            String queueStatus
    ) {
        return AnalysisInfoDto.builder()
                .status(status)
                .queueStatus(queueStatus)
                .analysisRequestId(request.getAnalysisRequestId())
                .requestedAt(AnalysisDetailFormatters.formatUtc(request.getRequestedAt()))
                .completedAt(AnalysisDetailFormatters.formatUtc(request.getCompletedAt()))
                .summary(pendingSummary(status))
                .completed(false)
                .moduleResults(List.of())
                .modelScores(List.of())
                .evidenceItems(List.of())
                .frameRisks(List.of())
                .suspiciousSegments(List.of())
                .clipRisks(List.of())
                .pairRisks(List.of())
                .temporalSuspiciousSegments(List.of())
                .opticalSuspiciousSegments(List.of())
                .moduleTimelines(List.of())
                .representativeFrames(List.of())
                .modelOverlayArtifacts(List.of());
    }

    private AnalysisInfoDto.AnalysisInfoDtoBuilder completedBuilder(
            AnalysisRequest request,
            AnalysisResult result,
            List<AnalysisModuleResult> moduleResults,
            String status,
            String queueStatus
    ) {
        VideoModuleDetailsReader.VisualizationData visualization = videoModuleDetailsReader
                .readVisualization(moduleResults);

        return AnalysisInfoDto.builder()
                .status(status)
                .queueStatus(queueStatus)
                .analysisRequestId(request.getAnalysisRequestId())
                .requestedAt(AnalysisDetailFormatters.formatUtc(request.getRequestedAt()))
                .completedAt(AnalysisDetailFormatters.formatUtc(result.getAnalyzedAt()))
                .riskScore(result.getRiskScore())
                .confidenceScore(result.getConfidenceScore())
                .riskLevel(result.getRiskLevel() != null ? result.getRiskLevel().name() : null)
                .summary(result.getSummary() != null ? result.getSummary() : "분석이 완료되었습니다.")
                .completed(true)
                .moduleResults(moduleResults.stream().map(AnalysisDetailFormatters::toModuleResult).toList())
                .modelScores(AnalysisDetailFormatters.toModelScores(moduleResults))
                .evidenceItems(visualization.evidenceItems())
                .frameRisks(visualization.frameRisks())
                .suspiciousSegments(visualization.suspiciousSegments())
                .clipRisks(visualization.clipRisks())
                .pairRisks(visualization.pairRisks())
                .temporalSuspiciousSegments(visualization.temporalSuspiciousSegments())
                .opticalSuspiciousSegments(visualization.opticalSuspiciousSegments())
                .moduleTimelines(visualizationArtifactUrlRefresher.refreshModuleTimelines(visualization.moduleTimelines()))
                .representativeFrames(visualizationArtifactUrlRefresher.refreshFrames(visualization.representativeFrames()))
                .overlayVideoUrl(visualizationArtifactUrlRefresher.refresh(visualization.overlayVideoUrl()))
                .modelOverlayArtifacts(visualizationArtifactUrlRefresher.refreshArtifacts(visualization.modelOverlayArtifacts()))
                // Soft-complete advisories (e.g. NO_HUMAN_FACE) while status remains COMPLETED.
                .errorCode(request.getErrorCode())
                .errorMessage(request.getErrorMessage());
    }

    private String pendingSummary(String status) {
        return switch (status) {
            case "PROCESSING" -> "AI 모델이 증거를 분석하고 있습니다.";
            case "FAILED" -> "분석 요청이 실패했습니다.";
            case "COMPLETED" -> "분석이 완료되었습니다.";
            default -> "분석 대기열에 등록되었습니다. AI 모델 연동 후 순차적으로 분석됩니다.";
        };
    }
}
