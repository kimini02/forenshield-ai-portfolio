package com.example.demo.service.compare;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.CompareBlockchainStatus;
import com.example.demo.domain.enums.CompareSignatureStatus;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.compare.CompareBlockchainInfoDto;
import com.example.demo.dto.compare.CompareSignatureInfoDto;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.service.blockchain.BlockchainHashIntegrityEvaluator;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.util.ApiDateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompareTrustMetadataAssembler {

    private final EvidenceManifestService evidenceManifestService;
    private final BlockchainAnchorRepository blockchainAnchorRepository;

    public CompareSignatureInfoDto buildSignatureInfo(Evidence original) {
        Optional<EvidenceManifest> manifest = evidenceManifestService.findByEvidenceId(original.getEvidenceId());
        if (manifest.isEmpty()) {
            return CompareSignatureInfoDto.builder()
                    .originalStatus(CompareSignatureStatus.UNSIGNED)
                    .candidateStatus(CompareSignatureStatus.UNSIGNED)
                    .build();
        }

        EvidenceManifest evidenceManifest = manifest.get();
        return CompareSignatureInfoDto.builder()
                .originalStatus(mapOriginalSignatureStatus(evidenceManifest))
                .candidateStatus(CompareSignatureStatus.UNSIGNED)
                .algorithm(evidenceManifest.getSignatureAlgorithm())
                .signedBy(evidenceManifest.getSignerCertificateSubject())
                .signedAt(ApiDateTimeFormatter.formatUtc(evidenceManifest.getSignedAt()))
                .build();
    }

    public CompareBlockchainInfoDto buildBlockchainInfo(Evidence original) {
        Optional<BlockchainAnchor> anchor = blockchainAnchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        original.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                );

        if (anchor.isEmpty() || anchor.get().getStatus() != BlockchainAnchorStatus.ANCHORED) {
            return CompareBlockchainInfoDto.builder()
                    .status(CompareBlockchainStatus.NOT_ANCHORED)
                    .build();
        }

        BlockchainAnchor anchored = anchor.get();
        CompareBlockchainStatus status = BlockchainHashIntegrityEvaluator.anchoredOriginalHashMatches(original, anchored)
                ? CompareBlockchainStatus.MATCH
                : CompareBlockchainStatus.MISMATCH;

        return CompareBlockchainInfoDto.builder()
                .status(status)
                .network(anchored.getNetwork())
                .txHash(anchored.getTransactionHash())
                .blockNumber(anchored.getBlockNumber())
                .anchoredAt(ApiDateTimeFormatter.formatUtc(anchored.getAnchoredAt()))
                .anchoredHash(anchored.getSubjectHash())
                .build();
    }

    private CompareSignatureStatus mapOriginalSignatureStatus(EvidenceManifest manifest) {
        SignatureStatus status = manifest.getSignatureStatus() != null
                ? manifest.getSignatureStatus()
                : SignatureStatus.UNSIGNED;
        return switch (status) {
            case UNSIGNED -> CompareSignatureStatus.UNSIGNED;
            case FAILED -> CompareSignatureStatus.INVALID;
            case SIGNED -> evidenceManifestService.isSignatureValid(manifest)
                    ? CompareSignatureStatus.VALID
                    : CompareSignatureStatus.INVALID;
        };
    }
}
