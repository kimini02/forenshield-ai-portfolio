package com.example.demo.dto;

import com.example.demo.dto.detail.ModuleTimelineDto;
import com.example.demo.dto.detail.ModelOverlayArtifactDto;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VideoDeepfakeTimelineDto {

    private List<FrameRiskDto> frameRisks;
    private List<SuspiciousSegmentDto> suspiciousSegments;
    private List<ClipRiskDto> clipRisks;
    private List<PairRiskDto> pairRisks;
    private List<SuspiciousSegmentDto> temporalSuspiciousSegments;
    private List<SuspiciousSegmentDto> opticalSuspiciousSegments;
    private List<ModuleTimelineDto> moduleTimelines;
    private List<RepresentativeFrameDto> representativeFrames;
    private String overlayVideoUrl;
    private List<ModelOverlayArtifactDto> modelOverlayArtifacts;
}
