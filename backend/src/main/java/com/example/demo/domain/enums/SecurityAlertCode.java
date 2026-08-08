package com.example.demo.domain.enums;

/** RQ-SEC-153 / SK-632: 보안 무결성 검증 실패 유형 */
public enum SecurityAlertCode {
    SIGNATURE_INVALID,
    CHAIN_INTEGRITY_FAILED,
    BLOCKCHAIN_HASH_MISMATCH,
    BLOCKCHAIN_CERT_MISMATCH
}
