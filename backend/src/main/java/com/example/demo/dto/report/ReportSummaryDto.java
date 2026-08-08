package com.example.demo.dto.report;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportSummaryDto {

    private Long reportId;
    private String reportType;
    private Long evidenceId;
    private Long compareId;
    private String caseId;
    private String caseName;
    private String reportFileName;
    private String verdictLabel;
    private String createdAt;
    private String reportHash;
    private String publicationStatus;
    private Integer version;
    private String issuedAt;
    private String downloadPath;
}
