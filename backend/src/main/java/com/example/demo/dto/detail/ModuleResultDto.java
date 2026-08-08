package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ModuleResultDto {

    private String moduleName;
    private boolean detected;
    private double score;
    private Double confidence;
    private String modelName;
    private String modelVersion;
    private String details;
}
