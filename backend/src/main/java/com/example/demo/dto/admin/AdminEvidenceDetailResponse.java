package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminEvidenceDetailResponse {

    private String id;
    private String fileName;
    private String fileType;
    private String mimeType;
    private long fileSize;
    private String caseNumber;
    private String caseName;
    private String uploaderUsername;
    private String uploaderName;
    private String department;
    private String hashAlgorithm;
    private String hashValue;
    private String uploadedAt;
    private String status;
    private String deletedAt;
    private String analysisStatus;
    private AdminEvidenceMetadataResponse metadata;
    private List<AdminEvidenceAnalysisResponse> analysisHistory;
    private List<AdminEvidenceCustodyLogResponse> custodyLogs;
}
