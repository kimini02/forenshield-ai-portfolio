package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceInfoDto {

    private Long evidenceId;
    private String fileName;
    private String displayLabel;
    private String originalFileName;
    private String caseName;
    private String caseId;
    private Long fileSize;
    private String uploadedAt;
    private String mediaType;
    private String fileType;
    private String lifecycleStatus;
    private String role;
    private Long replacementEvidenceId;
    private String excludedReason;
    private VideoMetadataDto technicalMetadata;
}
