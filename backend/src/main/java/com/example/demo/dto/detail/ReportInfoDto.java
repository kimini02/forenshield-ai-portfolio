package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportInfoDto {

    private Long reportId;
    private String reportFileName;
    private String verificationCode;
    private String reportHash;
    private String publicationStatus;
    private String createdAt;
}
