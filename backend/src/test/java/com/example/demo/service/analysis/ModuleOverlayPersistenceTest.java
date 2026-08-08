package com.example.demo.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.demo.dto.detail.ModelOverlayArtifactDto;
import com.example.demo.dto.detail.ModuleTimelineDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModuleOverlayPersistenceTest {

    @Mock
    private S3AnalysisAccessService s3AnalysisAccessService;

    private VideoModuleDetailsReader reader;
    private VisualizationArtifactUrlRefresher refresher;

    @BeforeEach
    void setUp() {
        reader = new VideoModuleDetailsReader(new ObjectMapper());
        refresher = new VisualizationArtifactUrlRefresher(s3AnalysisAccessService);
        ReflectionTestUtils.setField(refresher, "evidenceBucket", "forenshield-evidence-000000000000");
    }

    @Test
    void readModuleTimelines_includesOverlayVideoUrl() {
        Map<String, Object> details = Map.of(
                "moduleTimelines", List.of(
                        Map.of(
                                "module", "temporal",
                                "modelName", "TimeSformer",
                                "modelVersion", "v1",
                                "videoScore", 0.7,
                                "threshold", 0.6,
                                "detected", true,
                                "overlayVideoUrl",
                                "https://forenshield-evidence-000000000000.s3.amazonaws.com/a/overlay_temporal.mp4?old=1"
                        )
                )
        );

        List<ModuleTimelineDto> timelines = reader.readModuleTimelines(details);
        assertThat(timelines).hasSize(1);
        assertThat(timelines.get(0).getOverlayVideoUrl()).contains("overlay_temporal.mp4");

        when(s3AnalysisAccessService.createPresignedOriginalUrl(eq("a/overlay_temporal.mp4")))
                .thenReturn("https://fresh.example/overlay_temporal.mp4");

        List<ModuleTimelineDto> refreshed = refresher.refreshModuleTimelines(timelines);
        assertThat(refreshed.get(0).getOverlayVideoUrl()).isEqualTo("https://fresh.example/overlay_temporal.mp4");
    }

    @Test
    void readModelOverlayArtifacts_andRefresh() {
        Map<String, Object> details = Map.of(
                "modelOverlayArtifacts", List.of(
                        Map.of(
                                "key", "deepfake:optical",
                                "category", "deepfake",
                                "label", "GMFlow",
                                "overlayVideoUrl",
                                "https://forenshield-evidence-000000000000.s3.amazonaws.com/a/overlay_optical.mp4?old=1",
                                "status", "ready",
                                "description", "flow"
                        )
                )
        );

        List<ModelOverlayArtifactDto> artifacts = reader.readModelOverlayArtifacts(details);
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).getKey()).isEqualTo("deepfake:optical");

        when(s3AnalysisAccessService.createPresignedOriginalUrl(eq("a/overlay_optical.mp4")))
                .thenReturn("https://fresh.example/overlay_optical.mp4");

        List<ModelOverlayArtifactDto> refreshed = refresher.refreshArtifacts(artifacts);
        assertThat(refreshed.get(0).getOverlayVideoUrl()).isEqualTo("https://fresh.example/overlay_optical.mp4");
        assertThat(refreshed.get(0).getStatus()).isEqualTo("ready");
    }
}
