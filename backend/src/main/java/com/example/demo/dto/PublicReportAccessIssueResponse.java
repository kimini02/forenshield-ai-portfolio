package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicReportAccessIssueResponse {

    private final Long reportId;
    private final String reportNo;
    private final String accessCode;
    private final Boolean enabled;
    private final String publicViewUrl;
    private final String issuedAt;
    private final String expiresAt;
}
