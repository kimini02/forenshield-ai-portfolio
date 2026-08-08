package com.example.demo.service.custody;

import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.SecurityAlertCode;
import com.example.demo.dto.CocChainVerifyResponse;
import com.example.demo.dto.IntegrityCheckItem;
import com.example.demo.service.custody.CustodyLogService.TargetChainVerifyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for evidence CoC hash-chain verification.
 * Used by integrity checks, dedicated CoC API, and detail views.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustodyChainVerifier {

    private final CustodyLogService custodyLogService;

    public TargetChainVerifyResult verifyEvidenceChain(Long evidenceId) {
        return custodyLogService.verifyTargetChain(CustodyTargetType.EVIDENCE, evidenceId);
    }

    public boolean isEvidenceChainValid(Long evidenceId) {
        return verifyEvidenceChain(evidenceId).valid();
    }

    public IntegrityCheckItem toIntegrityCheckItem(TargetChainVerifyResult result) {
        if (result.valid()) {
            return IntegrityCheckItem.builder()
                    .checkType("COC_CHAIN")
                    .valid(true)
                    .message("CoC 해시 체인이 유효합니다.")
                    .build();
        }
        return IntegrityCheckItem.builder()
                .checkType("COC_CHAIN")
                .valid(false)
                .errorCode(SecurityAlertCode.CHAIN_INTEGRITY_FAILED.name())
                .message("증거 관리(CoC) 해시 체인 무결성 검증에 실패했습니다.")
                .build();
    }

    public CocChainVerifyResponse toCocChainResponse(Long evidenceId, TargetChainVerifyResult result) {
        if (result.valid()) {
            String message = result.logCount() == 0
                    ? "검증할 감사 로그가 없습니다."
                    : "CoC 해시 체인 검증에 성공했습니다.";
            return CocChainVerifyResponse.builder()
                    .evidenceId(evidenceId)
                    .valid(true)
                    .logCount(result.logCount())
                    .message(message)
                    .build();
        }

        return CocChainVerifyResponse.builder()
                .evidenceId(evidenceId)
                .valid(false)
                .logCount(result.logCount())
                .brokenAtLogId(result.brokenAtLogId())
                .failureReason(result.failureReason())
                .message(resolveCocFailureMessage(result.failureReason()))
                .build();
    }

    private String resolveCocFailureMessage(String failureReason) {
        return switch (failureReason) {
            case "PREVIOUS_HASH_MISMATCH" -> "로그 체인 연결이 끊어졌습니다.";
            case "HASH_MISMATCH" -> "로그 해시 재계산 결과가 일치하지 않습니다.";
            default -> "CoC 해시 체인 검증에 실패했습니다.";
        };
    }
}
