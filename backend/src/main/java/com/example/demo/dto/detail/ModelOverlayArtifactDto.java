package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ModelOverlayArtifactDto {

    private String key;
    private String category;
    private String label;
    private String overlayVideoUrl;
    private String status;
    private String description;
}
