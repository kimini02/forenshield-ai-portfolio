package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEvidenceItemResponse {

    private String id;
    private String fileName;
    private String fileType;
    private String caseNumber;
    private String caseName;
    private String uploaderUsername;
    private String uploaderName;
    private String department;
    private String hashValue;
    private long fileSize;
    private String uploadedAt;
    private String status;
    private String analysisStatus;
}
