package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEvidenceMetadataResponse {

    private Integer width;
    private Integer height;
    private Integer durationSec;
    private Double fps;
    private String codec;
    private Integer sampleRate;
    private Integer channels;
    private String deviceInfo;
    private String extractionStatus;
}
