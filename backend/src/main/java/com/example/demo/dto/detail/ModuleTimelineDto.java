package com.example.demo.dto.detail;

import com.example.demo.dto.ClipRiskDto;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.PairRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ModuleTimelineDto {

    private String module;
    private String modelName;
    private String modelVersion;
    private double videoScore;
    private double threshold;
    private boolean detected;
    private List<FrameRiskDto> frameRisks;
    private List<ClipRiskDto> clipRisks;
    private List<PairRiskDto> pairRisks;
    private List<SuspiciousSegmentDto> suspiciousSegments;
    private String overlayVideoUrl;
}
