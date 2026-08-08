package com.example.demo.service.blockchain;

import com.example.demo.config.BlockchainAnchorProperties;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.Report;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.detail.BlockchainInfoDto;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.service.blockchain.client.BlockchainAnchorClient;
import com.example.demo.service.blockchain.client.BlockchainAnchorRequest;
import com.example.demo.service.blockchain.client.BlockchainAnchorResult;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.service.notification.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockchainAnchorServiceTest {

    @Mock
    private BlockchainAnchorProperties properties;

    @Mock
    private BlockchainAnchorClient anchorClient;

    @Mock
    private BlockchainAnchorRepository anchorRepository;

    @Mock
    private CustodyLogRepository custodyLogRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private AnalysisModuleResultRepository analysisModuleResultRepository;

    @Mock
    private EvidenceAccessService evidenceAccessService;

    @Mock
    private EvidenceManifestService evidenceManifestService;

    @Mock
    private OffchainLogHashService offchainLogHashService;

    @Mock
    private HashService hashService;

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BlockchainAnchorService blockchainAnchorService;

    @Test
    void anchorEvidenceHash_persistsAnchoredRecordWithExtendedFields() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getNetwork()).thenReturn("local-simulated");
        when(properties.getClientId()).thenReturn("forenshield-be");
        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(any(), eq(BlockchainAnchorType.EVIDENCE_HASH)))
                .thenReturn(Optional.empty());
        when(anchorClient.anchor(any(BlockchainAnchorRequest.class)))
                .thenReturn(new BlockchainAnchorResult("0xtx", 1L, true, null));
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> {
            BlockchainAnchor anchor = invocation.getArgument(0);
            anchor.setAnchorId(10L);
            return anchor;
        });

        Evidence evidence = Evidence.builder()
                .uploaderId(5L)
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(1L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc")
                .originalStoragePath("path/original.mp4")
                .uploadedAt(LocalDateTime.now())
                .build();

        EvidenceManifest manifest = new EvidenceManifest();
        manifest.setEvidenceId(null);
        manifest.setSignatureStatus(SignatureStatus.SIGNED);
        manifest.setSignatureValue("sig-value");
        manifest.setSignerCertificateHash("cert-hash");
        manifest.setManifestStoragePath("path/manifest.json");
        when(evidenceManifestService.ensureManifest(evidence)).thenReturn(manifest);
        when(evidenceManifestService.isSignatureValid(manifest)).thenReturn(true);
        when(offchainLogHashService.hashEvidenceCustodyBundle(any())).thenReturn("offchain-hash");

        BlockchainAnchor result = blockchainAnchorService.anchorEvidenceHash(evidence, 5L);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(result.getTransactionHash()).isEqualTo("0xtx");
        assertThat(result.getSignatureValue()).isEqualTo("sig-value");
        assertThat(result.getSignerCertificateHash()).isEqualTo("cert-hash");
        assertThat(result.getCertVerified()).isTrue();
        assertThat(result.getOffchainLogHash()).isEqualTo("offchain-hash");

        ArgumentCaptor<BlockchainAnchorRequest> requestCaptor = ArgumentCaptor.forClass(BlockchainAnchorRequest.class);
        verify(anchorClient).anchor(requestCaptor.capture());
        BlockchainAnchorRequest request = requestCaptor.getValue();
        assertThat(request.signature()).isEqualTo("sig-value");
        assertThat(request.signerCertHash()).isEqualTo("cert-hash");
        assertThat(request.certVerified()).isTrue();
        assertThat(request.offchainLogHash()).isEqualTo("offchain-hash");
        assertThat(request.offchainRef().manifestStoragePath()).isEqualTo("path/manifest.json");
        assertThat(request.offchainRef().originalStoragePath()).isEqualTo("path/original.mp4");

        verify(notificationService).notifyBlockchainAnchored(eq(5L), isNull(), eq(BlockchainAnchorType.EVIDENCE_HASH), eq("0xtx"));
    }

    @Test
    void anchorEvidenceHash_holdsWithoutTxWhenCertNotVerified() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getNetwork()).thenReturn("local-simulated");
        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(any(), eq(BlockchainAnchorType.EVIDENCE_HASH)))
                .thenReturn(Optional.empty());
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Evidence evidence = Evidence.builder()
                .uploaderId(5L)
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(1L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc")
                .originalStoragePath("path")
                .uploadedAt(LocalDateTime.now())
                .build();

        EvidenceManifest manifest = new EvidenceManifest();
        manifest.setSignatureStatus(SignatureStatus.FAILED);
        manifest.setSignatureValue(null);
        when(evidenceManifestService.ensureManifest(evidence)).thenReturn(manifest);
        when(evidenceManifestService.isSignatureValid(manifest)).thenReturn(false);

        BlockchainAnchor result = blockchainAnchorService.anchorEvidenceHash(evidence, 5L);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.FAILED);
        assertThat(result.getErrorCode()).isEqualTo(BlockchainAnchorService.ERROR_MANIFEST_SIGNATURE_INVALID);
        assertThat(result.getCertVerified()).isFalse();
        assertThat(result.getTransactionHash()).isNull();
        verify(anchorClient, never()).anchor(any());
        verify(notificationService, never()).notifyBlockchainAnchored(any(), any(), any(), any());
    }

    @Test
    void anchorDailyMerkleRoot_skipsWhenBatchAlreadyExists() {
        when(properties.isEnabled()).thenReturn(true);
        LocalDate batchDate = LocalDate.of(2026, 6, 16);
        when(anchorRepository.existsByMerkleBatchDateAndAnchorType(batchDate, BlockchainAnchorType.MERKLE_ROOT))
                .thenReturn(true);
        when(anchorRepository.findTopByMerkleBatchDateAndAnchorTypeOrderByCreatedAtDesc(batchDate, BlockchainAnchorType.MERKLE_ROOT))
                .thenReturn(Optional.of(new BlockchainAnchor()));

        BlockchainAnchor result = blockchainAnchorService.anchorDailyMerkleRoot(batchDate);

        assertThat(result).isNotNull();
    }

    @Test
    void anchorDailyMerkleRoot_buildsRootFromCustodyLogsWithOffchainFields() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getNetwork()).thenReturn("local-simulated");
        when(properties.getClientId()).thenReturn("forenshield-be");
        LocalDate batchDate = LocalDate.of(2026, 6, 16);
        when(anchorRepository.existsByMerkleBatchDateAndAnchorType(batchDate, BlockchainAnchorType.MERKLE_ROOT))
                .thenReturn(false);

        CustodyLog log = new CustodyLog();
        log.setCurrentLogHash("1111111111111111111111111111111111111111111111111111111111111111");
        log.setCreatedAt(batchDate.atTime(10, 0));
        when(custodyLogRepository.findAll()).thenReturn(List.of(log));
        when(offchainLogHashService.hashDailyCustodyBundle(batchDate)).thenReturn("daily-offchain");
        when(anchorClient.anchor(any(BlockchainAnchorRequest.class)))
                .thenReturn(new BlockchainAnchorResult("0xmerkle", 2L, true, null));
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BlockchainAnchor result = blockchainAnchorService.anchorDailyMerkleRoot(batchDate);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(result.getMerkleLeafCount()).isEqualTo(1);
        assertThat(result.getSubjectHash()).isEqualTo(log.getCurrentLogHash());
        assertThat(result.getOffchainLogHash()).isEqualTo("daily-offchain");

        ArgumentCaptor<BlockchainAnchorRequest> requestCaptor = ArgumentCaptor.forClass(BlockchainAnchorRequest.class);
        verify(anchorClient).anchor(requestCaptor.capture());
        assertThat(requestCaptor.getValue().offchainRef().custodyLogBundleRef())
                .isEqualTo("rds:custody_logs?batchDate=2026-06-16");
        assertThat(requestCaptor.getValue().signature()).isNull();
        assertThat(requestCaptor.getValue().certVerified()).isNull();
    }

    @Test
    void anchorReportHash_includesAnalysisModelMetadata() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getNetwork()).thenReturn("local-simulated");
        when(properties.getClientId()).thenReturn("forenshield-be");
        when(anchorRepository.findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(any(), eq(BlockchainAnchorType.REPORT_HASH)))
                .thenReturn(Optional.empty());
        when(anchorClient.anchor(any(BlockchainAnchorRequest.class)))
                .thenReturn(new BlockchainAnchorResult("0xreport", 3L, true, null));
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Report report = new Report();
        report.setReportId(22L);
        report.setEvidenceId(7L);
        report.setAnalysisResultId(55L);
        report.setReportHash("report-hash");
        report.setStoragePath("reports/22.pdf");

        Evidence evidence = Evidence.builder()
                .originalStoragePath("evidence/original.mp4")
                .build();
        when(evidenceRepository.findById(7L)).thenReturn(Optional.of(evidence));
        when(offchainLogHashService.hashReportBundle(report)).thenReturn("report-offchain");

        com.example.demo.domain.AnalysisModuleResult fusion = new com.example.demo.domain.AnalysisModuleResult();
        fusion.setModuleName("deepfake");
        fusion.setModelName("Late Fusion");
        fusion.setModelVersion("late-fusion-v4-ts-gated");

        com.example.demo.domain.AnalysisModuleResult cnn = new com.example.demo.domain.AnalysisModuleResult();
        cnn.setModuleName("deepfake_cnn");
        cnn.setModelName("Xception");
        cnn.setModelVersion("xception/v1.1.0-celeb1k");

        when(analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(55L))
                .thenReturn(List.of(fusion, cnn));

        BlockchainAnchor result = blockchainAnchorService.anchorReportHash(report, 5L);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(result.getAnalysisModelJson()).contains("late-fusion-v4-ts-gated");
        assertThat(result.getAnalysisModulesJson()).contains("xception/v1.1.0-celeb1k");

        ArgumentCaptor<BlockchainAnchorRequest> requestCaptor = ArgumentCaptor.forClass(BlockchainAnchorRequest.class);
        verify(anchorClient).anchor(requestCaptor.capture());
        assertThat(requestCaptor.getValue().analysisModel()).isNotNull();
        assertThat(requestCaptor.getValue().analysisModel().version()).isEqualTo("late-fusion-v4-ts-gated");
        assertThat(requestCaptor.getValue().analysisModules()).hasSize(2);
    }

    @Test
    void anchorReportHash_includesForgeryModelMetadata() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getNetwork()).thenReturn("local-simulated");
        when(properties.getClientId()).thenReturn("forenshield-be");
        when(anchorRepository.findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(any(), eq(BlockchainAnchorType.REPORT_HASH)))
                .thenReturn(Optional.empty());
        when(anchorClient.anchor(any(BlockchainAnchorRequest.class)))
                .thenReturn(new BlockchainAnchorResult("0xreport-forgery", 4L, true, null));
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Report report = new Report();
        report.setReportId(23L);
        report.setEvidenceId(8L);
        report.setAnalysisResultId(56L);
        report.setReportHash("report-hash-forgery");
        report.setStoragePath("reports/23.pdf");

        Evidence evidence = Evidence.builder()
                .originalStoragePath("evidence/original-forgery.mp4")
                .build();
        when(evidenceRepository.findById(8L)).thenReturn(Optional.of(evidence));
        when(offchainLogHashService.hashReportBundle(report)).thenReturn("report-offchain-forgery");

        com.example.demo.domain.AnalysisModuleResult fusion = new com.example.demo.domain.AnalysisModuleResult();
        fusion.setModuleName("deepfake");
        fusion.setModelName("Late Fusion");
        fusion.setModelVersion("late-fusion-v4-ts-gated");

        com.example.demo.domain.AnalysisModuleResult spatial = new com.example.demo.domain.AnalysisModuleResult();
        spatial.setModuleName("forgery_spatial");
        spatial.setModelName("TruFor");
        spatial.setModelVersion("trufor-videocof-v2-20260710-0800");

        com.example.demo.domain.AnalysisModuleResult temporal = new com.example.demo.domain.AnalysisModuleResult();
        temporal.setModuleName("forgery_temporal");
        temporal.setModelName("TimeSformer-Forgery");
        temporal.setModelVersion("timesformer-forgery-v1.9-hardneg-20260714-0342");

        when(analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(56L))
                .thenReturn(List.of(fusion, spatial, temporal));

        BlockchainAnchor result = blockchainAnchorService.anchorReportHash(report, 5L);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(result.getAnalysisModulesJson()).contains("forgery_spatial");
        assertThat(result.getAnalysisModulesJson()).contains("trufor-videocof-v2-20260710-0800");
        assertThat(result.getAnalysisModulesJson()).contains("forgery_temporal");
        assertThat(result.getAnalysisModulesJson()).contains("timesformer-forgery-v1.9-hardneg-20260714-0342");

        ArgumentCaptor<BlockchainAnchorRequest> requestCaptor = ArgumentCaptor.forClass(BlockchainAnchorRequest.class);
        verify(anchorClient).anchor(requestCaptor.capture());
        assertThat(requestCaptor.getValue().analysisModules()).hasSize(3);
        assertThat(requestCaptor.getValue().analysisModules())
                .extracting(BlockchainAnchorRequest.AnalysisModuleRef::module)
                .containsExactly("deepfake", "forgery_spatial", "forgery_temporal");
    }

    @Test
    void getEvidenceBlockchainInfo_reportsHashIntegrityAndExplorerUrl() {
        when(properties.getExplorerUrlTemplate()).thenReturn("https://explorer.test/tx/{txHash}");

        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(7L);
        when(evidence.getOriginalHashValue()).thenReturn("abc");

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash("abc");
        anchor.setTransactionHash("0xabc123");
        anchor.setNetwork("local-simulated");
        anchor.setAnchoredAt(LocalDateTime.now());
        anchor.setCertVerified(true);

        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                7L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        BlockchainInfoDto info = blockchainAnchorService.getEvidenceBlockchainInfo(evidence);

        assertThat(info.getHashValid()).isTrue();
        assertThat(info.getCertVerified()).isTrue();
        assertThat(info.getVerificationMessage()).contains("일치");
        assertThat(info.getTransactionExplorerUrl()).isEqualTo("https://explorer.test/tx/0xabc123");
    }

    @Test
    void getEvidenceBlockchainInfo_reportsHeldWhenManifestSignatureInvalid() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(8L);

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(BlockchainAnchorStatus.FAILED);
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash("abc");
        anchor.setErrorCode(BlockchainAnchorService.ERROR_MANIFEST_SIGNATURE_INVALID);
        anchor.setCertVerified(false);

        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                8L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        BlockchainInfoDto info = blockchainAnchorService.getEvidenceBlockchainInfo(evidence);

        assertThat(info.getHashValid()).isTrue();
        assertThat(info.getCertVerified()).isFalse();
        assertThat(info.getErrorCode()).isEqualTo(BlockchainAnchorService.ERROR_MANIFEST_SIGNATURE_INVALID);
        assertThat(info.getVerificationMessage()).contains("보류");
    }

    @Test
    void getEvidenceBlockchainInfo_reportsMismatchWhenHashesDiffer() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(8L);
        when(evidence.getOriginalHashValue()).thenReturn("current-hash");

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash("anchored-hash");

        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                8L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        BlockchainInfoDto info = blockchainAnchorService.getEvidenceBlockchainInfo(evidence);

        assertThat(info.getHashValid()).isFalse();
        assertThat(info.getVerificationMessage()).contains("일치하지 않습니다");
    }

    @Test
    void findAnchoredEvidenceSubjectHash_returnsLatestAnchoredHash() {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setSubjectHash("registered-hash");

        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                9L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        assertThat(blockchainAnchorService.findAnchoredEvidenceSubjectHash(9L))
                .contains("registered-hash");
    }
}
