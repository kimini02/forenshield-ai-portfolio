package com.example.demo.service.integrity;

import com.example.demo.domain.Evidence;
import com.example.demo.dto.IntegrityVerifyResponse;

/**
 * 증거 1건에 대한 무결성 검증 결과 (상세 조회 시 중복 검증 방지용).
 */
public record EvidenceIntegrityResult(Evidence evidence, IntegrityVerifyResponse verification) {
}
