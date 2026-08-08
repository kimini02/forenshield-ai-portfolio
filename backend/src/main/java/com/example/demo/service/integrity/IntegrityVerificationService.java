package com.example.demo.service.integrity;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.SecurityAlertCode;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.IntegrityCheckItem;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.blockchain.BlockchainHashIntegrityEvaluator;
import com.example.demo.service.custody.CustodyChainVerifier;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.service.notification.NotificationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrityVerificationService {

    private final EvidenceAccessService evidenceAccessService;
    private final EvidenceManifestService evidenceManifestService;
    private final CustodyChainVerifier custodyChainVerifier;
    private final BlockchainAnchorRepository blockchainAnchorRepository;
    private final NotificationService notificationService;

    /**
     * RQ-SEC-153: 상세 조회 등에서 무결성 검증 후 실패 시 보안 알림 자동 발송.
     */
    @Transactional
    public EvidenceIntegrityResult verifyAndNotifySecurityIssues(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireReadable(user, evidenceId);
        IntegrityVerifyResponse response = buildVerification(evidence);
        dispatchSecurityAlerts(user.getUserId(), evidenceId, response);
        return new EvidenceIntegrityResult(evidence, response);
    }

    /**
     * SK-632: 전용 검증 API — 실패 시 409 + errorCode.
     */
    @Transactional
    public IntegrityVerifyResponse verifyIntegrityOrThrow(User user, Long evidenceId) {
        EvidenceIntegrityResult result = verifyAndNotifySecurityIssues(user, evidenceId);
        IntegrityVerifyResponse response = result.verification();
        if (!response.isValid()) {
            IntegrityCheckItem firstFailure = response.getChecks().stream()
                    .filter(check -> !check.isValid())
                    .findFirst()
                    .orElseThrow();
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    firstFailure.getErrorCode(),
                    firstFailure.getMessage()
            );
        }
        return response;
    }

    private IntegrityVerifyResponse buildVerification(Evidence evidence) {
        Long evidenceId = evidence.getEvidenceId();
        List<IntegrityCheckItem> checks = new ArrayList<>();
        checks.add(checkManifestSignature(evidenceId));
        checks.add(checkCustodyChain(evidenceId));
        checks.addAll(checkBlockchain(evidence));

        boolean valid = checks.stream().allMatch(IntegrityCheckItem::isValid);
        return IntegrityVerifyResponse.builder()
                .evidenceId(evidenceId)
                .valid(valid)
                .checks(checks)
                .build();
    }

    private IntegrityCheckItem checkManifestSignature(Long evidenceId) {
        return evidenceManifestService.findByEvidenceId(evidenceId)
                .map(this::evaluateManifestSignature)
                .orElse(IntegrityCheckItem.builder()
                        .checkType("SIGNATURE")
                        .valid(true)
                        .message("Manifest가 아직 생성되지 않았습니다.")
                        .build());
    }

    private IntegrityCheckItem evaluateManifestSignature(EvidenceManifest manifest) {
        SignatureStatus status = manifest.getSignatureStatus();
        if (status == SignatureStatus.FAILED) {
            return invalidSignature("Manifest 전자서명 생성에 실패했습니다.");
        }
        if (status == SignatureStatus.UNSIGNED) {
            return IntegrityCheckItem.builder()
                    .checkType("SIGNATURE")
                    .valid(true)
                    .message("서명 대기 상태입니다.")
                    .build();
        }
        if (!evidenceManifestService.isSignatureValid(manifest)) {
            return invalidSignature("Evidence Manifest X.509 서명 검증에 실패했습니다.");
        }
        return IntegrityCheckItem.builder()
                .checkType("SIGNATURE")
                .valid(true)
                .message("Manifest 서명이 유효합니다.")
                .build();
    }

    private IntegrityCheckItem invalidSignature(String message) {
        return IntegrityCheckItem.builder()
                .checkType("SIGNATURE")
                .valid(false)
                .errorCode(SecurityAlertCode.SIGNATURE_INVALID.name())
                .message(message)
                .build();
    }

    private IntegrityCheckItem checkCustodyChain(Long evidenceId) {
        return custodyChainVerifier.toIntegrityCheckItem(
                custodyChainVerifier.verifyEvidenceChain(evidenceId)
        );
    }

    private List<IntegrityCheckItem> checkBlockchain(Evidence evidence) {
        Optional<BlockchainAnchor> latest = blockchainAnchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        evidence.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                );

        if (latest.isEmpty()) {
            return List.of(IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_HASH")
                    .valid(true)
                    .message("앵커링된 블록체인 기록이 없습니다.")
                    .build());
        }

        BlockchainAnchor anchor = latest.get();
        List<IntegrityCheckItem> checks = new ArrayList<>();

        if (anchor.getStatus() == BlockchainAnchorStatus.FAILED
                && BlockchainAnchorService.ERROR_MANIFEST_SIGNATURE_INVALID.equals(anchor.getErrorCode())) {
            checks.add(IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_HASH")
                    .valid(true)
                    .message("원본 해시는 저장되었으나 매니페스트 서명 실패로 블록체인 앵커가 없습니다.")
                    .build());
            checks.add(IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_CERT")
                    .valid(true)
                    .message("앵커 시점 certVerified=false — Fabric TX 없음(보류).")
                    .build());
            return checks;
        }

        if (anchor.getStatus() != BlockchainAnchorStatus.ANCHORED) {
            checks.add(IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_HASH")
                    .valid(true)
                    .message("블록체인 앵커가 아직 완료되지 않았습니다. status=" + anchor.getStatus())
                    .build());
            return checks;
        }

        checks.add(evaluateBlockchainHash(evidence, anchor));
        checks.add(evaluateBlockchainCert(evidence.getEvidenceId(), anchor));
        return checks;
    }

    private IntegrityCheckItem evaluateBlockchainHash(Evidence evidence, BlockchainAnchor anchor) {
        if (BlockchainHashIntegrityEvaluator.anchoredOriginalHashMatches(evidence, anchor)) {
            return IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_HASH")
                    .valid(true)
                    .message("블록체인 앵커 해시가 현재 원본 해시와 일치합니다.")
                    .build();
        }
        return IntegrityCheckItem.builder()
                .checkType("BLOCKCHAIN_HASH")
                .valid(false)
                .errorCode(SecurityAlertCode.BLOCKCHAIN_HASH_MISMATCH.name())
                .message("블록체인에 등록된 해시와 현재 증거 원본 해시가 일치하지 않습니다.")
                .build();
    }

    private IntegrityCheckItem evaluateBlockchainCert(Long evidenceId, BlockchainAnchor anchor) {
        if (anchor.getCertVerified() == null) {
            return IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_CERT")
                    .valid(true)
                    .message("앵커에 certVerified 스냅샷이 없습니다.")
                    .build();
        }

        boolean currentValid = evidenceManifestService.findByEvidenceId(evidenceId)
                .map(evidenceManifestService::isSignatureValid)
                .orElse(false);

        if (Objects.equals(anchor.getCertVerified(), currentValid)) {
            return IntegrityCheckItem.builder()
                    .checkType("BLOCKCHAIN_CERT")
                    .valid(true)
                    .message("원장 certVerified(" + anchor.getCertVerified()
                            + ")와 현재 서명 재검증 결과가 일치합니다.")
                    .build();
        }

        return IntegrityCheckItem.builder()
                .checkType("BLOCKCHAIN_CERT")
                .valid(false)
                .errorCode(SecurityAlertCode.BLOCKCHAIN_CERT_MISMATCH.name())
                .message("원장 certVerified(" + anchor.getCertVerified()
                        + ")와 현재 서명 재검증(" + currentValid + ")이 일치하지 않습니다.")
                .build();
    }

    private void dispatchSecurityAlerts(Long userId, Long evidenceId, IntegrityVerifyResponse response) {
        for (IntegrityCheckItem check : response.getChecks()) {
            if (check.isValid() || check.getErrorCode() == null) {
                continue;
            }
            SecurityAlertCode alertCode = SecurityAlertCode.valueOf(check.getErrorCode());
            notificationService.notifySecurityAlertIfNeeded(userId, evidenceId, alertCode);
        }
    }

}
