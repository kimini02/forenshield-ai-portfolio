package com.example.demo.service.blockchain;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.BlockchainAnchorStatus;

/**
 * Shared logic for comparing evidence original hash against blockchain anchor subject hash.
 */
public final class BlockchainHashIntegrityEvaluator {

    private BlockchainHashIntegrityEvaluator() {
    }

    public static boolean anchoredOriginalHashMatches(Evidence evidence, BlockchainAnchor anchor) {
        if (evidence == null || anchor == null) {
            return false;
        }
        String currentHash = evidence.getOriginalHashValue();
        return currentHash != null && currentHash.equalsIgnoreCase(anchor.getSubjectHash());
    }

    public static HashIntegrityResult evaluate(Evidence evidence, BlockchainAnchor anchor) {
        if (anchor.getStatus() != BlockchainAnchorStatus.ANCHORED) {
            return new HashIntegrityResult(null, statusMessage(anchor));
        }
        if (anchoredOriginalHashMatches(evidence, anchor)) {
            return new HashIntegrityResult(
                    true,
                    "블록체인 등록 해시와 현재 원본 해시가 일치합니다."
            );
        }
        return new HashIntegrityResult(
                false,
                "블록체인 등록 해시와 현재 원본 해시가 일치하지 않습니다."
        );
    }

    private static String statusMessage(BlockchainAnchor anchor) {
        return switch (anchor.getStatus()) {
            case PENDING -> "블록체인 앵커링이 진행 중입니다.";
            case FAILED -> anchor.getErrorMessage() == null
                    ? "블록체인 앵커링에 실패했습니다."
                    : anchor.getErrorMessage();
            case ANCHORED -> null;
        };
    }

    public record HashIntegrityResult(Boolean hashValid, String verificationMessage) {
    }
}
