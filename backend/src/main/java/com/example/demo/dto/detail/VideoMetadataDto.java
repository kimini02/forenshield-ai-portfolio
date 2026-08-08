package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VideoMetadataDto {
    private String extractionStatus;
    private Integer width;
    private Integer height;
    private Double durationSec;
    private Double fps;
    private Integer sampleRate;
    private Integer channels;
    private Boolean hasAudioTrack;
    private String codec;
}
