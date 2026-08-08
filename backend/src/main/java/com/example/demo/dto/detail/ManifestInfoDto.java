package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

/** RQ-DTL-075: Evidence Manifest 요약 */
@Getter
@Builder
public class ManifestInfoDto {

    private Long evidenceId;
    /** SK-624: evidenceId와 동일 (파일 식별자) */
    private Long fileId;
    /** SK-624: 사건 식별자 (caseNumber 우선) */
    private String caseId;
    private String caseNumber;
    private String caseName;
    private String fileName;
    /** SK-624: 원본 업로드 일시 (UTC) */
    private String uploadedAt;
    private String hashAlgorithm;
    private String originalHash;
    private String copyHash;
    /** ISO-8601 UTC */
    private String manifestCreatedAt;
    private String manifestHash;
    private String issuer;
}
