package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OverlayJobStatusResponse {

    private Long overlayJobId;
    private Long evidenceId;
    private String module;
    private String status;
    private int progressPercent;
    private String overlayVideoUrl;
    private String errorCode;
    private String errorMessage;
}
