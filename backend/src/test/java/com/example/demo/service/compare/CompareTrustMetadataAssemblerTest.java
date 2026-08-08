package com.example.demo.service.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.CompareBlockchainStatus;
import com.example.demo.domain.enums.CompareSignatureStatus;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.service.manifest.EvidenceManifestService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompareTrustMetadataAssemblerTest {

    @Mock
    private EvidenceManifestService evidenceManifestService;

    @Mock
    private BlockchainAnchorRepository blockchainAnchorRepository;

    @InjectMocks
    private CompareTrustMetadataAssembler assembler;

    @Test
    void buildSignatureInfo_withoutManifest_returnsUnsignedForBoth() {
        Evidence evidence = evidence(1L);

        when(evidenceManifestService.findByEvidenceId(1L)).thenReturn(Optional.empty());

        var result = assembler.buildSignatureInfo(evidence);

        assertThat(result.getOriginalStatus()).isEqualTo(CompareSignatureStatus.UNSIGNED);
        assertThat(result.getCandidateStatus()).isEqualTo(CompareSignatureStatus.UNSIGNED);
    }

    @Test
    void buildSignatureInfo_withValidSignedManifest_mapsOriginalValid() {
        Evidence evidence = evidence(2L);
        EvidenceManifest manifest = new EvidenceManifest();
        manifest.setEvidenceId(2L);
        manifest.setSignatureStatus(SignatureStatus.SIGNED);
        manifest.setSignatureAlgorithm("RSA-SHA256");
        manifest.setSignerCertificateSubject("CN=ForenShield Evidence CA");
        manifest.setSignedAt(LocalDateTime.of(2026, 6, 27, 3, 42));

        when(evidenceManifestService.findByEvidenceId(2L)).thenReturn(Optional.of(manifest));
        when(evidenceManifestService.isSignatureValid(manifest)).thenReturn(true);

        var result = assembler.buildSignatureInfo(evidence);

        assertThat(result.getOriginalStatus()).isEqualTo(CompareSignatureStatus.VALID);
        assertThat(result.getCandidateStatus()).isEqualTo(CompareSignatureStatus.UNSIGNED);
        assertThat(result.getAlgorithm()).isEqualTo("RSA-SHA256");
        assertThat(result.getSignedBy()).isEqualTo("CN=ForenShield Evidence CA");
        assertThat(result.getSignedAt()).isNotBlank();
    }

    @Test
    void buildBlockchainInfo_withoutAnchor_returnsNotAnchored() {
        Evidence evidence = evidence(3L);

        when(blockchainAnchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                3L, BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.empty());

        var result = assembler.buildBlockchainInfo(evidence);

        assertThat(result.getStatus()).isEqualTo(CompareBlockchainStatus.NOT_ANCHORED);
    }

    @Test
    void buildBlockchainInfo_withMatchingAnchor_returnsMatchDetails() {
        String hash = "abc123def4567890abc123def4567890abc123def4567890abc123def4567890";
        Evidence evidence = evidenceWithHash(4L, hash);

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setSubjectHash(hash);
        anchor.setNetwork("ForenShield Chain");
        anchor.setTransactionHash("0xabc");
        anchor.setBlockNumber(1842907L);
        anchor.setAnchoredAt(LocalDateTime.of(2026, 6, 27, 3, 43, 10));

        when(blockchainAnchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                4L, BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        var result = assembler.buildBlockchainInfo(evidence);

        assertThat(result.getStatus()).isEqualTo(CompareBlockchainStatus.MATCH);
        assertThat(result.getNetwork()).isEqualTo("ForenShield Chain");
        assertThat(result.getTxHash()).isEqualTo("0xabc");
        assertThat(result.getBlockNumber()).isEqualTo(1842907L);
        assertThat(result.getAnchoredHash()).isEqualTo(hash);
    }

    @Test
    void buildBlockchainInfo_withMismatchedHash_returnsMismatch() {
        Evidence evidence = evidenceWithHash(5L, "abc123def4567890abc123def4567890abc123def4567890abc123def4567890");

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setSubjectHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

        when(blockchainAnchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                5L, BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        var result = assembler.buildBlockchainInfo(evidence);

        assertThat(result.getStatus()).isEqualTo(CompareBlockchainStatus.MISMATCH);
        assertThat(result.getAnchoredHash()).isEqualTo(anchor.getSubjectHash());
    }

    private Evidence evidence(Long id) {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(id);
        return evidence;
    }

    private Evidence evidenceWithHash(Long id, String hash) {
        Evidence evidence = evidence(id);
        when(evidence.getOriginalHashValue()).thenReturn(hash);
        return evidence;
    }
}
