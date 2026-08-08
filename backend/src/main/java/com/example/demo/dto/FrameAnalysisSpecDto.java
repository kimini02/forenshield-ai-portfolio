package com.example.demo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FrameAnalysisSpecDto {

    /** SK-668 */
    private double extractionIntervalSec;

    /** SK-674 */
    private double highRiskFrameScoreThreshold;

    /** SK-675 */
    private double minSuspiciousSegmentSec;

    /** SK-672 */
    private String pixelFormat;

    /** SK-672 */
    private String imageEncoding;

    /** SK-670: 분석 대상 프레임 시각(초) */
    private List<Double> sampleTimestampsSec;
}
