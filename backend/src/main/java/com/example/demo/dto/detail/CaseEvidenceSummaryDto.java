package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseEvidenceSummaryDto {

    private Long evidenceId;
    private String fileName;
    private String displayLabel;
    private String originalFileName;
    private String mediaType;
    private String analysisStatus;
    private Integer analysisProgress;
    private String lifecycleStatus;
    private String role;
    private Long replacementEvidenceId;
    private String excludedReason;
    private String hlsStatus;
}
