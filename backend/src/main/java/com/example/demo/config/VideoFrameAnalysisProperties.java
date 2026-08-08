package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.frame")
public class VideoFrameAnalysisProperties {

    /** SK-668: 프레임 샘플링 간격(초) */
    private double extractionIntervalSec = 1.0;

    /** SK-674: 고위험 프레임 판정 기준 (0.0~1.0) */
    private double highRiskFrameScoreThreshold = 0.70;

    /** SK-675: 의심 구간 최소 길이(초) */
    private double minSuspiciousSegmentSec = 0.5;

    /** SK-672: AI 모델 입력 픽셀 포맷 */
    private String pixelFormat = "RGB24";

    /** SK-672: AI 모델 입력 이미지 인코딩 */
    private String imageEncoding = "jpeg";

    /** MQ 메시지에 포함할 최대 샘플 타임스탬프 수 */
    private int maxSampleTimestamps = 120;
}
