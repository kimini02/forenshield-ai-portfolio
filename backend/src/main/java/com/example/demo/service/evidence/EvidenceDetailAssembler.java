package com.example.demo.service.evidence;

import com.example.demo.config.EvidenceManifestProperties;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.IntegrityCheckItem;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.dto.detail.CocLogDto;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.dto.detail.EvidenceInfoDto;
import com.example.demo.dto.detail.HlsPlaybackDto;
import com.example.demo.dto.detail.IntegrityInfoDto;
import com.example.demo.dto.detail.ManifestInfoDto;
import com.example.demo.dto.detail.RecoveryScoreDto;
import com.example.demo.dto.detail.ReportInfoDto;
import com.example.demo.dto.detail.SignatureInfoDto;
import com.example.demo.dto.detail.VideoMetadataDto;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.AnalysisInfoAssembler;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.CustodyChainVerifier;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvidenceDetailAssembler {

    private final EvidenceRepository evidenceRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CustodyChainVerifier custodyChainVerifier;
    private final BlockchainAnchorService blockchainAnchorService;
    private final EvidenceManifestService evidenceManifestService;
    private final EvidenceManifestProperties evidenceManifestProperties;
    private final AnalysisInfoAssembler analysisInfoAssembler;
    private final CaseEvidencePresentationService caseEvidencePresentationService;

    public EvidenceDetailResponse assemble(
            Evidence evidence,
            IntegrityVerifyResponse verification,
            EvidenceMetadata metadata,
            AnalysisRequest request,
            AnalysisResult result,
            List<AnalysisModuleResult> moduleResults,
            List<CustodyLog> custodyLogs,
            EvidenceManifest manifest,
            RecoveryScoreDto recovery,
            HlsPlaybackDto hlsPlayback
    ) {
        boolean isChainValid = resolveChainValid(verification, evidence.getEvidenceId());

        return EvidenceDetailResponse.builder()
                .evidenceInfo(toEvidenceInfo(evidence, metadata))
                .integrityInfo(toIntegrityInfo(evidence, isChainValid, recovery, custodyLogs.size()))
                .manifestInfo(toManifestInfo(evidence, manifest))
                .signatureInfo(toSignatureInfo(manifest, verification))
                .blockchainInfo(blockchainAnchorService.getEvidenceBlockchainInfo(evidence))
                .analysisInfo(analysisInfoAssembler.assemble(request, result, moduleResults))
                .reportInfo(toReportInfo(evidence.getEvidenceId()))
                .cocLogs(toCocLogs(custodyLogs))
                .hlsPlayback(hlsPlayback)
                .build();
    }

    private ReportInfoDto toReportInfo(Long evidenceId) {
        return reportRepository
                .findTopByEvidenceIdAndCompareIdIsNullOrderByCreatedAtDesc(evidenceId)
                .map(this::toReportInfo)
                .orElse(null);
    }

    private ReportInfoDto toReportInfo(Report report) {
        return ReportInfoDto.builder()
                .reportId(report.getReportId())
                .reportFileName(report.getReportFileName())
                .verificationCode(report.getVerificationCode())
                .reportHash(report.getReportHash())
                .publicationStatus(report.getPublicationStatus() == null
                        ? null
                        : report.getPublicationStatus().name())
                .createdAt(ApiDateTimeFormatter.formatUtc(report.getCreatedAt()))
                .build();
    }

    private boolean resolveChainValid(IntegrityVerifyResponse verification, Long evidenceId) {
        if (verification != null) {
            return findCheck(verification, "COC_CHAIN")
                    .map(IntegrityCheckItem::isValid)
                    .orElse(true);
        }
        return custodyChainVerifier.isEvidenceChainValid(evidenceId);
    }

    private Optional<IntegrityCheckItem> findCheck(IntegrityVerifyResponse verification, String checkType) {
        if (verification == null || verification.getChecks() == null) {
            return Optional.empty();
        }
        return verification.getChecks().stream()
                .filter(check -> checkType.equals(check.getCheckType()))
                .findFirst();
    }

    private EvidenceInfoDto toEvidenceInfo(Evidence evidence, EvidenceMetadata metadata) {
        List<Evidence> caseEvidences = evidenceRepository.findByUploaderIdAndCaseKey(
                evidence.getUploaderId(),
                EvidenceCaseIdResolver.resolve(evidence)
        );
        return EvidenceInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileName(evidence.getFileName())
                .displayLabel(caseEvidencePresentationService.resolveDisplayLabel(evidence, caseEvidences))
                .originalFileName(evidence.getFileName())
                .caseName(evidence.getCaseName())
                .caseId(EvidenceCaseIdResolver.resolve(evidence))
                .fileSize(evidence.getFileSize())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
                .mediaType(evidence.getFileType().name())
                .fileType(evidence.getFileType().name())
                .lifecycleStatus(caseEvidencePresentationService.lifecycleStatusName(evidence))
                .role(caseEvidencePresentationService.roleName(evidence))
                .replacementEvidenceId(evidence.getReplacementEvidenceId())
                .excludedReason(evidence.getExcludedReason())
                .technicalMetadata(mapToTypeSpecificMetadata(metadata))
                .build();
    }

    private VideoMetadataDto mapToTypeSpecificMetadata(EvidenceMetadata metadata) {
        if (metadata == null) {
            return VideoMetadataDto.builder()
                    .extractionStatus(ExtractionStatus.FAILED.name())
                    .build();
        }

        String extractionStatus = metadata.getExtractionStatus() == null
                ? ExtractionStatus.FAILED.name()
                : metadata.getExtractionStatus().name();

        return VideoMetadataDto.builder()
                .extractionStatus(extractionStatus)
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .durationSec(metadata.getDurationSec() != null ? metadata.getDurationSec().doubleValue() : null)
                .fps(metadata.getFps())
                .codec(metadata.getCodec())
                .sampleRate(metadata.getSampleRate())
                .channels(metadata.getChannels())
                .hasAudioTrack(metadata.getSampleRate() != null || metadata.getChannels() != null)
                .build();
    }

    private IntegrityInfoDto toIntegrityInfo(
            Evidence evidence,
            boolean isChainValid,
            RecoveryScoreDto recovery,
            int cocLogCount
    ) {
        return IntegrityInfoDto.builder()
                .hashAlgorithm(evidence.getHashAlgorithm())
                .originalHash(evidence.getOriginalHashValue())
                .copyHash(evidence.getCopyHashValue())
                .copyStatus(evidence.getCopyStatus() != null ? evidence.getCopyStatus().name() : null)
                .chainValid(isChainValid)
                .chainValidAlias(isChainValid)
                .verificationStatus(isChainValid ? "VERIFIED" : "CORRUPTED")
                .recoveryScore(recovery.getRecoveryScore())
                .dataLossPercent(recovery.getDataLossPercent())
                .recoveryGrade(recovery.getGrade())
                .cocLogCount(cocLogCount)
                .cocChainVerified(isChainValid)
                .cocVerificationMessage(isChainValid
                        ? "CoC 해시 체인이 유효합니다."
                        : "CoC 해시 체인 검증에 실패했습니다.")
                .build();
    }

    private ManifestInfoDto toManifestInfo(Evidence evidence, EvidenceManifest manifest) {
        if (manifest == null) {
            return null;
        }
        return ManifestInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileId(evidence.getEvidenceId())
                .caseId(EvidenceCaseIdResolver.resolve(evidence))
                .caseNumber(evidence.getCaseNumber())
                .caseName(evidence.getCaseName())
                .fileName(evidence.getFileName())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
                .hashAlgorithm(evidence.getHashAlgorithm())
                .originalHash(evidence.getOriginalHashValue())
                .copyHash(evidence.getCopyHashValue())
                .manifestCreatedAt(ApiDateTimeFormatter.formatUtc(manifest.getCreatedAt()))
                .manifestHash(manifest.getManifestHash())
                .issuer(evidenceManifestProperties.getIssuer())
                .build();
    }

    private SignatureInfoDto toSignatureInfo(EvidenceManifest manifest, IntegrityVerifyResponse verification) {
        if (manifest == null) {
            return SignatureInfoDto.builder()
                    .signatureStatus(SignatureStatus.UNSIGNED.name())
                    .build();
        }
        SignatureStatus status = manifest.getSignatureStatus() != null
                ? manifest.getSignatureStatus()
                : SignatureStatus.UNSIGNED;
        Boolean valid = null;
        if (status == SignatureStatus.SIGNED) {
            if (verification != null) {
                valid = findCheck(verification, "SIGNATURE")
                        .map(IntegrityCheckItem::isValid)
                        .orElse(null);
            } else {
                valid = evidenceManifestService.isSignatureValid(manifest);
            }
        }
        return SignatureInfoDto.builder()
                .signatureStatus(status.name())
                .signatureAlgorithm(manifest.getSignatureAlgorithm())
                .signedAt(ApiDateTimeFormatter.formatUtc(manifest.getSignedAt()))
                .signerCertificateSubject(manifest.getSignerCertificateSubject())
                .signatureValid(valid)
                .build();
    }

    private List<CocLogDto> toCocLogs(List<CustodyLog> custodyLogs) {
        return custodyLogs.stream().map(this::toCocLog).toList();
    }

    private CocLogDto toCocLog(CustodyLog log) {
        String actor = userRepository.findById(log.getActorId())
                .map(User::getLoginId)
                .orElse("SYSTEM");

        return CocLogDto.builder()
                .logId(log.getLogId())
                .eventType(log.getActionType())
                .userId(actor)
                .description(log.getReason() != null ? log.getReason() : log.getActionType())
                .createdAt(ApiDateTimeFormatter.formatUtc(log.getCreatedAt()))
                .currentLogHash(log.getCurrentLogHash())
                .build();
    }
}
