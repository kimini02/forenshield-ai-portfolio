package com.example.demo.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.demo.dto.RepresentativeFrameDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VisualizationArtifactUrlRefresherTest {

    @Mock
    private S3AnalysisAccessService s3AnalysisAccessService;

    private VisualizationArtifactUrlRefresher refresher;

    @BeforeEach
    void setUp() {
        refresher = new VisualizationArtifactUrlRefresher(s3AnalysisAccessService);
        ReflectionTestUtils.setField(refresher, "evidenceBucket", "forenshield-evidence-000000000000");
    }

    @Test
    void resolveEvidenceObjectKey_fromVirtualHostedHttpsUrl() {
        String key = refresher.resolveEvidenceObjectKey(
                "https://forenshield-evidence-000000000000.s3.amazonaws.com/deepfake/artifacts/analysis/158/161/overlay.mp4?X-Amz-Expires=604800"
        );

        assertThat(key).isEqualTo("deepfake/artifacts/analysis/158/161/overlay.mp4");
    }

    @Test
    void refresh_returnsNewPresignedUrlForStoredArtifact() {
        String stored = "https://forenshield-evidence-000000000000.s3.amazonaws.com/deepfake/artifacts/analysis/158/161/overlay.mp4?expired=1";
        when(s3AnalysisAccessService.createPresignedOriginalUrl(
                eq("deepfake/artifacts/analysis/158/161/overlay.mp4")))
                .thenReturn("https://fresh.example/overlay.mp4");

        assertThat(refresher.refresh(stored)).isEqualTo("https://fresh.example/overlay.mp4");
    }

    @Test
    void refreshFrames_refreshesImageUrls() {
        when(s3AnalysisAccessService.createPresignedOriginalUrl(eq("deepfake/artifacts/analysis/158/161/frame.jpg")))
                .thenReturn("https://fresh.example/frame.jpg");

        List<RepresentativeFrameDto> refreshed = refresher.refreshFrames(List.of(
                RepresentativeFrameDto.builder()
                        .imageUrl("https://forenshield-evidence-000000000000.s3.amazonaws.com/deepfake/artifacts/analysis/158/161/frame.jpg?old=1")
                        .build()
        ));

        assertThat(refreshed.get(0).getImageUrl()).isEqualTo("https://fresh.example/frame.jpg");
    }
}
