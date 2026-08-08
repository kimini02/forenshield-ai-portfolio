package com.example.demo.service.analysis;

import com.example.demo.service.evidence.MediaService;
import com.example.demo.config.VideoFrameAnalysisProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class VideoFrameExtractionServiceTest {

    @Mock
    private MediaService mediaService;

    private VideoFrameExtractionService service;

    @BeforeEach
    void setUp() {
        VideoFrameAnalysisProperties properties = new VideoFrameAnalysisProperties();
        properties.setExtractionIntervalSec(1.0);
        properties.setHighRiskFrameScoreThreshold(0.70);
        properties.setMaxSampleTimestamps(10);
        service = new VideoFrameExtractionService(mediaService, properties, "");
    }

    @Test
    @DisplayName("SK-668: 영상 길이에 따라 샘플링 타임스탬프를 생성한다")
    void buildSampleTimestamps_generatesIntervalSamples() {
        List<Double> timestamps = service.buildSampleTimestamps(3.5);

        assertThat(timestamps).containsExactly(0.0, 1.0, 2.0, 3.0);
    }

    @Test
    @DisplayName("SK-672: 프레임 분석 스펙에 모델 입력 포맷을 포함한다")
    void buildSpecForDuration_includesModelInputFormat() {
        var spec = service.buildSpecForDuration(2.0);

        assertThat(spec.getPixelFormat()).isEqualTo("RGB24");
        assertThat(spec.getImageEncoding()).isEqualTo("jpeg");
        assertThat(spec.getSampleTimestampsSec()).containsExactly(0.0, 1.0);
        assertThat(spec.getHighRiskFrameScoreThreshold()).isEqualTo(0.70);
    }
}
