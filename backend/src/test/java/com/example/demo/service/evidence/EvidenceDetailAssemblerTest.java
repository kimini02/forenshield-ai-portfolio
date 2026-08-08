package com.example.demo.service.evidence;

import com.example.demo.config.EvidenceManifestProperties;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.Report;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.IntegrityCheckItem;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.dto.detail.HlsPlaybackDto;
import com.example.demo.dto.detail.RecoveryScoreDto;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.AnalysisInfoAssembler;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.CustodyChainVerifier;
import com.example.demo.service.manifest.EvidenceManifestService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceDetailAssemblerTest {

    @Mock
    private EvidenceRepository evidenceRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustodyChainVerifier custodyChainVerifier;
    @Mock
    private BlockchainAnchorService blockchainAnchorService;
    @Mock
    private EvidenceManifestService evidenceManifestService;
    @Mock
    private EvidenceManifestProperties evidenceManifestProperties;
    @Mock
    private AnalysisInfoAssembler analysisInfoAssembler;
    @Mock
    private CaseEvidencePresentationService caseEvidencePresentationService;

    private EvidenceDetailAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new EvidenceDetailAssembler(
                evidenceRepository,
                reportRepository,
                userRepository,
                custodyChainVerifier,
                blockchainAnchorService,
                evidenceManifestService,
                evidenceManifestProperties,
                analysisInfoAssembler,
                caseEvidencePresentationService
        );
    }

    @Test
    void assemble_reusesVerificationForChainAndSignature() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(7L);
        when(evidence.getUploaderId()).thenReturn(1L);
        when(evidence.getCaseName()).thenReturn("case-a");
        when(evidence.getFileName()).thenReturn("video.mp4");
        when(evidence.getFileType()).thenReturn(FileType.VIDEO);
        when(evidence.getUploadedAt()).thenReturn(LocalDateTime.now());
        when(evidence.getHashAlgorithm()).thenReturn("SHA-256");
        when(evidence.getOriginalHashValue()).thenReturn("a".repeat(64));

        EvidenceManifest manifest = mock(EvidenceManifest.class);
        when(manifest.getSignatureStatus()).thenReturn(SignatureStatus.SIGNED);

        IntegrityVerifyResponse verification = IntegrityVerifyResponse.builder()
                .checks(List.of(
                        IntegrityCheckItem.builder().checkType("COC_CHAIN").valid(false).build(),
                        IntegrityCheckItem.builder().checkType("SIGNATURE").valid(true).build()
                ))
                .build();
        RecoveryScoreDto recovery = RecoveryScoreDto.builder()
                .recoveryScore(100)
                .dataLossPercent(0)
                .grade("A")
                .build();
        HlsPlaybackDto hlsPlayback = HlsPlaybackDto.builder()
                .manifestPath("/api/v1/evidences/7/hls/master.m3u8")
                .hlsStatus("PENDING")
                .streamToken("stream-token")
                .expiresIn(900)
                .build();

        when(evidenceRepository.findByUploaderIdAndCaseKey(1L, "case-a")).thenReturn(List.of(evidence));
        when(caseEvidencePresentationService.resolveDisplayLabel(any(), any())).thenReturn("증거 1");
        when(caseEvidencePresentationService.lifecycleStatusName(evidence)).thenReturn("ACTIVE");
        when(caseEvidencePresentationService.roleName(evidence)).thenReturn("PRIMARY");
        when(blockchainAnchorService.getEvidenceBlockchainInfo(evidence)).thenReturn(null);
        when(analysisInfoAssembler.assemble(null, null, List.of())).thenReturn(null);
        Report report = new Report();
        report.setReportId(91L);
        report.setReportFileName("analysis-report-7.pdf");
        report.setVerificationCode("VRF-20260717-000091");
        report.setReportHash("b".repeat(64));
        when(reportRepository.findTopByEvidenceIdAndCompareIdIsNullOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(report));

        var response = assembler.assemble(
                evidence,
                verification,
                null,
                null,
                null,
                List.of(),
                List.of(),
                manifest,
                recovery,
                hlsPlayback
        );

        assertThat(response.getIntegrityInfo().isChainValid()).isFalse();
        assertThat(response.getIntegrityInfo().getVerificationStatus()).isEqualTo("CORRUPTED");
        assertThat(response.getSignatureInfo().getSignatureValid()).isTrue();
        assertThat(response.getHlsPlayback().getStreamToken()).isEqualTo("stream-token");
        assertThat(response.getReportInfo().getVerificationCode()).isEqualTo("VRF-20260717-000091");
        assertThat(response.getReportInfo().getReportHash()).isEqualTo("b".repeat(64));
        verify(custodyChainVerifier, never()).isEvidenceChainValid(7L);
        verify(evidenceManifestService, never()).isSignatureValid(manifest);
    }
}
