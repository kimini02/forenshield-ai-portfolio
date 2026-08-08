package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RepresentativeFrameDto {

    private Double timeSec;
    private String timestamp;
    private Integer frameNumber;
    private Double score;
    private String imageUrl;
}
