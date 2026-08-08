package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ModelScoreDto {

    private String moduleName;
    private boolean detected;
    private double score;
    private String modelName;
    private String modelVersion;
}
