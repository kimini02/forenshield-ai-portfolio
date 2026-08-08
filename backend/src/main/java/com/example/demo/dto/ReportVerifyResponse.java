package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportVerifyResponse {

    private final boolean valid;
    private final Long reportId;
    private final Long evidenceId;
    private final String reportHash;
    private final String reportFileName;
    private final String createdAt;
    private final String message;
}
