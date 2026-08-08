package com.example.demo.service.report;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.service.blockchain.BlockchainHashIntegrityEvaluator;
import com.example.demo.service.custody.CustodyChainVerifier;
import com.example.demo.service.custody.CustodyLogService.TargetChainVerifyResult;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.util.ApiDateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RQ-DTL-084~088: 보고서 발행에 사용할 증거 무결성 상태를 부작용 없이 조회한다.
 * 보안 알림을 발송하는 IntegrityVerificationService와 분리하여 PDF 생성이 알림을 만들지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportIntegritySnapshotService {

    private final EvidenceManifestService evidenceManifestService;
    private final CustodyChainVerifier custodyChainVerifier;
    private final BlockchainAnchorRepository blockchainAnchorRepository;

    public IntegritySnapshot inspect(Evidence evidence) {
        if (evidence == null || evidence.getEvidenceId() == null) {
            throw new IllegalArgumentException("evidence and evidenceId are required");
        }

        ManifestSnapshot manifest = inspectManifest(evidence.getEvidenceId());
        CocSnapshot coc = inspectCoc(evidence.getEvidenceId());
        EvidenceBlockchainSnapshot blockchain = inspectEvidenceBlockchain(evidence);
        return new IntegritySnapshot(
                ApiDateTimeFormatter.formatUtc(LocalDateTime.now()),
                manifest.status(),
                manifest.algorithm(),
                manifest.certificateSubject(),
                coc.status(),
                coc.logCount(),
                coc.brokenAtLogId(),
                blockchain.status(),
                blockchain.network(),
                blockchain.transactionHash(),
                blockchain.anchoredAt()
        );
    }

    public List<String> toReportLines(Evidence evidence) {
        IntegritySnapshot snapshot = inspect(evidence);
        List<String> lines = new ArrayList<>();
        lines.add(" ");
        lines.add("=== Integrity Verification Snapshot ===");
        lines.add("Integrity Verified At: " + valueOrDash(snapshot.verifiedAt()));
        lines.add("Manifest Signature Status: " + snapshot.manifestSignatureStatus());
        lines.add("Manifest Signature Algorithm: " + valueOrDash(snapshot.manifestSignatureAlgorithm()));
        lines.add("Manifest Signer Certificate Subject: "
                + valueOrDash(snapshot.manifestSignerCertificateSubject()));
        lines.add("CoC Chain Status: " + snapshot.cocChainStatus());
        lines.add("CoC Log Count: " + snapshot.cocLogCount());
        lines.add("CoC Broken At Log ID: " + valueOrDash(snapshot.cocBrokenAtLogId()));
        lines.add("Evidence Blockchain Status: " + snapshot.evidenceBlockchainStatus());
        lines.add("Evidence Blockchain Network: " + valueOrDash(snapshot.evidenceBlockchainNetwork()));
        lines.add("Evidence Blockchain Transaction Hash: "
                + valueOrDash(snapshot.evidenceBlockchainTransactionHash()));
        lines.add("Evidence Blockchain Anchored At: "
                + valueOrDash(snapshot.evidenceBlockchainAnchoredAt()));
        return List.copyOf(lines);
    }

    private ManifestSnapshot inspectManifest(Long evidenceId) {
        EvidenceManifest manifest = evidenceManifestService.findByEvidenceId(evidenceId).orElse(null);
        if (manifest == null || manifest.getSignatureStatus() == null
                || manifest.getSignatureStatus() == SignatureStatus.UNSIGNED) {
            return new ManifestSnapshot("NOT_FOUND", null, null);
        }
        if (manifest.getSignatureStatus() == SignatureStatus.FAILED) {
            return new ManifestSnapshot(
                    "FAILED",
                    manifest.getSignatureAlgorithm(),
                    manifest.getSignerCertificateSubject()
            );
        }
        try {
            return new ManifestSnapshot(
                    evidenceManifestService.isSignatureValid(manifest) ? "VALID" : "INVALID",
                    manifest.getSignatureAlgorithm(),
                    manifest.getSignerCertificateSubject()
            );
        } catch (RuntimeException ex) {
            return new ManifestSnapshot(
                    "INVALID",
                    manifest.getSignatureAlgorithm(),
                    manifest.getSignerCertificateSubject()
            );
        }
    }

    private CocSnapshot inspectCoc(Long evidenceId) {
        TargetChainVerifyResult result = custodyChainVerifier.verifyEvidenceChain(evidenceId);
        if (result.logCount() == 0) {
            return new CocSnapshot("NOT_FOUND", 0, null);
        }
        return new CocSnapshot(
                result.valid() ? "VALID" : "INVALID",
                result.logCount(),
                result.brokenAtLogId()
        );
    }

    private EvidenceBlockchainSnapshot inspectEvidenceBlockchain(Evidence evidence) {
        BlockchainAnchor anchor = blockchainAnchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        evidence.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                )
                .orElse(null);
        if (anchor == null) {
            return new EvidenceBlockchainSnapshot("NOT_ANCHORED", null, null, null);
        }
        if (anchor.getStatus() == BlockchainAnchorStatus.PENDING) {
            return blockchainSnapshot("PENDING", anchor);
        }
        if (anchor.getStatus() == BlockchainAnchorStatus.FAILED) {
            return blockchainSnapshot("FAILED", anchor);
        }
        String status = BlockchainHashIntegrityEvaluator.anchoredOriginalHashMatches(evidence, anchor)
                ? "MATCHED"
                : "MISMATCHED";
        return blockchainSnapshot(status, anchor);
    }

    private EvidenceBlockchainSnapshot blockchainSnapshot(String status, BlockchainAnchor anchor) {
        return new EvidenceBlockchainSnapshot(
                status,
                anchor.getNetwork(),
                anchor.getTransactionHash(),
                ApiDateTimeFormatter.formatUtc(anchor.getAnchoredAt())
        );
    }

    private String valueOrDash(Object value) {
        return value == null || value.toString().isBlank() ? "-" : value.toString();
    }

    private record ManifestSnapshot(String status, String algorithm, String certificateSubject) {
    }

    private record CocSnapshot(String status, int logCount, Long brokenAtLogId) {
    }

    private record EvidenceBlockchainSnapshot(
            String status,
            String network,
            String transactionHash,
            String anchoredAt
    ) {
    }

    public record IntegritySnapshot(
            String verifiedAt,
            String manifestSignatureStatus,
            String manifestSignatureAlgorithm,
            String manifestSignerCertificateSubject,
            String cocChainStatus,
            int cocLogCount,
            Long cocBrokenAtLogId,
            String evidenceBlockchainStatus,
            String evidenceBlockchainNetwork,
            String evidenceBlockchainTransactionHash,
            String evidenceBlockchainAnchoredAt
    ) {
    }
}
