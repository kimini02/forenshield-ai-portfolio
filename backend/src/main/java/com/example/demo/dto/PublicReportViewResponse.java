package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicReportViewResponse {

    private final Long reportId;
    private final String reportNo;
    private final String reportType;
    private final Long evidenceId;
    private final Long compareId;
    private final String reportFileName;
    private final String reportHash;
    private final Long fileSize;
    private final String createdAt;
    private final String expiresAt;
    private final String downloadPath;
}
