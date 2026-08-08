package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

/** RQ-DTL-076: 전자서명 상태 */
@Getter
@Builder
public class SignatureInfoDto {

    /** SIGNED · UNSIGNED · FAILED */
    private String signatureStatus;
    private String signatureAlgorithm;
    /** ISO-8601 UTC */
    private String signedAt;
    private String signerCertificateSubject;
    /** 검증 가능 여부 (플랫폼 X.509 서명 검증 결과) */
    private Boolean signatureValid;
}
