package com.example.demo.service.custody;

import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.dto.CocChainVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RQ-HIS-107: 증거별 CoC 해시 체인 검증 API.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CocChainVerificationService {

    private final EvidenceAccessService evidenceAccessService;
    private final CustodyChainVerifier custodyChainVerifier;

    public CocChainVerifyResponse verifyEvidenceChain(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireReadable(user, evidenceId);
        return custodyChainVerifier.toCocChainResponse(
                evidence.getEvidenceId(),
                custodyChainVerifier.verifyEvidenceChain(evidence.getEvidenceId())
        );
    }
}
