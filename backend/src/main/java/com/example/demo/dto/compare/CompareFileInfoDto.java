package com.example.demo.dto.compare;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompareFileInfoDto {

    private Long evidenceId;
    private Long compareId;
    private String fileName;
    private Long fileSize;
    private String sha256;
    private String caseName;
    private String caseNumber;
    private String fileType;
    private String mimeType;
    private String uploadedAt;
}
