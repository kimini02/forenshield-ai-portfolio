package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {

    private boolean success;
    private String message;
    private Long evidenceId;
    private String fileName;
    private String caseName;
    private String caseNumber;
    private Long fileSize;
    private String hashAlgorithm;
    /** @deprecated 하위 호환용. 신규 연동은 {@link #originalSha256} 사용 */
    private String hashValue;
    /** 원본 파일 SHA-256 (hex 64자). AI·무결성 연동 표준 필드명 */
    private String originalSha256;
    private Object metadata;
    private Object readiness;
    private String displayLabel;
}
