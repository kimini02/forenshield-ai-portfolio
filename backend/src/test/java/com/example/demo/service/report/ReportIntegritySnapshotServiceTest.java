package com.example.demo.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.service.custody.CustodyChainVerifier;
import com.example.demo.service.custody.CustodyLogService.TargetChainVerifyResult;
import com.example.demo.service.manifest.EvidenceManifestService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportIntegritySnapshotServiceTest {

    @Mock
    private EvidenceManifestService evidenceManifestService;

    @Mock
    private CustodyChainVerifier custodyChainVerifier;

    @Mock
    private BlockchainAnchorRepository blockchainAnchorRepository;

    @InjectMocks
    private ReportIntegritySnapshotService service;

    @Test
    void snapshotsManifestCocAndEvidenceBlockchainWithoutChangingState() {
        Evidence evidence = evidence(101L);
        when(evidence.getOriginalHashValue()).thenReturn("a".repeat(64));
        EvidenceManifest manifest = new EvidenceManifest();
        manifest.setEvidenceId(101L);
        manifest.setSignatureStatus(SignatureStatus.SIGNED);
        manifest.setSignatureAlgorithm("SHA256withRSA");
        manifest.setSignerCertificateSubject("CN=ForenShield Test");

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setEvidenceId(101L);
        anchor.setSubjectHash("a".repeat(64));
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setNetwork("hyperledger-fabric-forenshield");
        anchor.setTransactionHash("tx-evidence-101");
        anchor.setAnchoredAt(LocalDateTime.of(2026, 7, 14, 9, 59));

        when(evidenceManifestService.findByEvidenceId(101L)).thenReturn(Optional.of(manifest));
        when(evidenceManifestService.isSignatureValid(manifest)).thenReturn(true);
        when(custodyChainVerifier.verifyEvidenceChain(101L))
                .thenReturn(new TargetChainVerifyResult(true, 7, null, null));
        when(blockchainAnchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                101L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        List<String> lines = service.toReportLines(evidence);

        assertThat(lines)
                .contains("Manifest Signature Status: VALID")
                .contains("Manifest Signature Algorithm: SHA256withRSA")
                .contains("Manifest Signer Certificate Subject: CN=ForenShield Test")
                .contains("CoC Chain Status: VALID")
                .contains("CoC Log Count: 7")
                .contains("Evidence Blockchain Status: MATCHED")
                .contains("Evidence Blockchain Network: hyperledger-fabric-forenshield")
                .contains("Evidence Blockchain Transaction Hash: tx-evidence-101");
    }

    @Test
    void recordsExplicitMissingStatesWhenIntegrityArtifactsDoNotExist() {
        Evidence evidence = evidence(202L);
        when(evidenceManifestService.findByEvidenceId(202L)).thenReturn(Optional.empty());
        when(custodyChainVerifier.verifyEvidenceChain(202L))
                .thenReturn(new TargetChainVerifyResult(true, 0, null, null));
        when(blockchainAnchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                202L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.empty());

        List<String> lines = service.toReportLines(evidence);

        assertThat(lines)
                .contains("Manifest Signature Status: NOT_FOUND")
                .contains("CoC Chain Status: NOT_FOUND")
                .contains("CoC Log Count: 0")
                .contains("Evidence Blockchain Status: NOT_ANCHORED");
    }

    private Evidence evidence(Long evidenceId) {
        Evidence evidence = org.mockito.Mockito.mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(evidenceId);
        return evidence;
    }
}
