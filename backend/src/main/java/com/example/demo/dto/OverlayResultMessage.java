package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OverlayResultMessage {

    private String jobType;
    private Long overlayJobId;
    private Long analysisRequestId;
    private Long evidenceId;
    private String module;
    private String status;
    private Integer progressPercent;
    private String overlayVideoUrl;
    private String analyzedAt;
    private String errorCode;
    private String message;
}
