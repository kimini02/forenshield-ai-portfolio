package com.example.demo.dto.readiness;

import lombok.Builder;
import lombok.Getter;
// 영상 기본 정보
// 해상도, FPS, 길이, 샘플링 정보 등
// ffprobe 또는 video_readienss.py 결과에서 채움


@Getter
@Builder
public class ReadinessVideoMetadataDto {

    private Integer width;
    private Integer height;
    private Double fps;
    private Integer durationSec;
    private Integer totalFrames;
    private Integer sampledFrames;
    private Integer sampleEvery;
}
