package com.example.demo.service.blockchain;

import com.example.demo.config.BlockchainAnchorProperties;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.dto.BlockchainAnchorRecordDto;
import com.example.demo.dto.BlockchainAnchorStatusResponse;
import com.example.demo.dto.detail.BlockchainInfoDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.service.blockchain.client.BlockchainAnchorClient;
import com.example.demo.service.blockchain.client.BlockchainAnchorRequest;
import com.example.demo.service.blockchain.client.BlockchainAnchorResult;
import com.example.demo.service.blockchain.client.OffchainRef;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.service.notification.NotificationService;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.MerkleTreeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainAnchorService {

    public static final String ERROR_MANIFEST_SIGNATURE_INVALID = "MANIFEST_SIGNATURE_INVALID";

    private final BlockchainAnchorProperties properties;
    private final BlockchainAnchorClient anchorClient;
    private final BlockchainAnchorRepository anchorRepository;
    private final CustodyLogRepository custodyLogRepository;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final EvidenceAccessService evidenceAccessService;
    private final EvidenceManifestService evidenceManifestService;
    private final OffchainLogHashService offchainLogHashService;
    private final HashService hashService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BlockchainAnchor anchorEvidenceHash(Evidence evidence, Long userId) {
        if (!properties.isEnabled() || evidence == null) {
            return null;
        }

        Optional<BlockchainAnchor> existingAnchored = anchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        evidence.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                )
                .filter(existing -> existing.getStatus() == BlockchainAnchorStatus.ANCHORED);
        if (existingAnchored.isPresent()) {
            return existingAnchored.get();
        }

        EvidenceManifest manifest = evidenceManifestService.ensureManifest(evidence);
        boolean certVerified = evidenceManifestService.isSignatureValid(manifest);
        String offchainLogHash = offchainLogHashService.hashEvidenceCustodyBundle(evidence.getEvidenceId());
        OffchainRef offchainRef = OffchainRef.ofEvidence(
                manifest.getManifestStoragePath(),
                evidence.getOriginalStoragePath()
        );

        if (!certVerified) {
            return persistFailedWithoutTx(
                    BlockchainAnchorType.EVIDENCE_HASH,
                    evidence.getOriginalHashValue(),
                    evidence.getEvidenceId(),
                    null,
                    userId,
                    null,
                    null,
                    manifest.getSignatureValue(),
                    manifest.getSignerCertificateHash(),
                    false,
                    offchainLogHash,
                    offchainRef,
                    null,
                    null,
                    ERROR_MANIFEST_SIGNATURE_INVALID,
                    "Manifest signature is not valid; blockchain anchor held."
            );
        }

        BlockchainAnchorRequest request = new BlockchainAnchorRequest(
                evidence.getOriginalHashValue(),
                BlockchainAnchorType.EVIDENCE_HASH,
                properties.getNetwork(),
                properties.getClientId(),
                evidence.getEvidenceId(),
                null,
                null,
                null,
                manifest.getSignatureValue(),
                manifest.getSignerCertificateHash(),
                true,
                offchainLogHash,
                offchainRef,
                null,
                null
        );
        return executeAnchor(request, userId);
    }

    @Transactional
    public BlockchainAnchor anchorReportHash(Report report, Long userId) {
        if (!properties.isEnabled() || report == null || report.getReportHash() == null) {
            return null;
        }

        Optional<BlockchainAnchor> existingAnchored = anchorRepository
                .findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                        report.getReportId(),
                        BlockchainAnchorType.REPORT_HASH
                )
                .filter(existing -> existing.getStatus() == BlockchainAnchorStatus.ANCHORED);
        if (existingAnchored.isPresent()) {
            return existingAnchored.get();
        }

        String originalStoragePath = evidenceRepository.findById(report.getEvidenceId())
                .map(Evidence::getOriginalStoragePath)
                .orElse(null);
        String offchainLogHash = offchainLogHashService.hashReportBundle(report);
        OffchainRef offchainRef = OffchainRef.ofReport(report.getStoragePath(), originalStoragePath);
        AnalysisAnchorMetadata analysisAnchorMetadata = buildAnalysisAnchorMetadata(report);

        // PDF signing not introduced yet — omit signature / certVerified.
        BlockchainAnchorRequest request = new BlockchainAnchorRequest(
                report.getReportHash(),
                BlockchainAnchorType.REPORT_HASH,
                properties.getNetwork(),
                properties.getClientId(),
                report.getEvidenceId(),
                report.getReportId(),
                null,
                null,
                null,
                null,
                null,
                offchainLogHash,
                offchainRef,
                analysisAnchorMetadata.analysisModel(),
                analysisAnchorMetadata.analysisModules()
        );
        return executeAnchor(request, userId);
    }

    @Transactional
    public BlockchainAnchor anchorDailyMerkleRoot(LocalDate batchDate) {
        if (!properties.isEnabled()) {
            return null;
        }

        LocalDate targetDate = batchDate == null ? LocalDate.now().minusDays(1) : batchDate;
        if (anchorRepository.existsByMerkleBatchDateAndAnchorType(targetDate, BlockchainAnchorType.MERKLE_ROOT)) {
            log.info("Merkle root already anchored for batchDate={}", targetDate);
            return anchorRepository.findTopByMerkleBatchDateAndAnchorTypeOrderByCreatedAtDesc(
                    targetDate,
                    BlockchainAnchorType.MERKLE_ROOT
            ).orElse(null);
        }

        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();
        List<String> leafHashes = custodyLogRepository.findAll().stream()
                .filter(logEntry -> logEntry.getCreatedAt() != null)
                .filter(logEntry -> !logEntry.getCreatedAt().isBefore(start)
                        && logEntry.getCreatedAt().isBefore(end))
                .map(CustodyLog::getCurrentLogHash)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (leafHashes.isEmpty()) {
            log.info("Skipping merkle anchor for batchDate={} — no custody logs", targetDate);
            return null;
        }

        String merkleRoot = MerkleTreeUtil.computeRoot(leafHashes, hashService);
        String offchainLogHash = offchainLogHashService.hashDailyCustodyBundle(targetDate);
        OffchainRef offchainRef = OffchainRef.ofMerkleBatch(targetDate.toString());

        BlockchainAnchorRequest request = new BlockchainAnchorRequest(
                merkleRoot,
                BlockchainAnchorType.MERKLE_ROOT,
                properties.getNetwork(),
                properties.getClientId(),
                null,
                null,
                targetDate.toString(),
                leafHashes.size(),
                null,
                null,
                null,
                offchainLogHash,
                offchainRef,
                null,
                null
        );
        return executeAnchor(request, null);
    }

    @Transactional
    public BlockchainAnchorRecordDto triggerMerkleRootAnchor(LocalDate batchDate) {
        if (!properties.isEnabled()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "BLOCKCHAIN_DISABLED",
                    "블록체인 앵커링이 비활성화되어 있습니다."
            );
        }
        LocalDate targetDate = batchDate == null ? LocalDate.now().minusDays(1) : batchDate;
        BlockchainAnchor anchor = anchorDailyMerkleRoot(targetDate);
        if (anchor == null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "MERKLE_ANCHOR_SKIPPED",
                    "해당 일자의 CoC 로그가 없어 Merkle Root 앵커를 생성할 수 없습니다."
            );
        }
        return toDto(anchor);
    }

    /**
     * Optional admin retry: re-run evidence hash anchor when previous attempt failed
     * (e.g. MANIFEST_SIGNATURE_INVALID) or was never anchored.
     */
    @Transactional
    public BlockchainAnchor retryEvidenceHashAnchor(Evidence evidence, Long userId) {
        return anchorEvidenceHash(evidence, userId);
    }

    @Transactional(readOnly = true)
    public BlockchainAnchorStatusResponse getEvidenceAnchorStatus(User user, Long evidenceId) {
        evidenceAccessService.requireReadable(user, evidenceId);

        BlockchainAnchorRecordDto evidenceAnchor = anchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(evidenceId, BlockchainAnchorType.EVIDENCE_HASH)
                .map(this::toDto)
                .orElse(null);

        List<BlockchainAnchorRecordDto> reportAnchors = anchorRepository
                .findByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(evidenceId, BlockchainAnchorType.REPORT_HASH)
                .stream()
                .map(this::toDto)
                .toList();

        BlockchainAnchorRecordDto latestMerkle = anchorRepository
                .findTopByMerkleBatchDateAndAnchorTypeOrderByCreatedAtDesc(
                        LocalDate.now().minusDays(1),
                        BlockchainAnchorType.MERKLE_ROOT
                )
                .map(this::toDto)
                .orElseGet(() -> anchorRepository.findAll().stream()
                        .filter(anchor -> anchor.getAnchorType() == BlockchainAnchorType.MERKLE_ROOT)
                        .filter(anchor -> anchor.getStatus() == BlockchainAnchorStatus.ANCHORED)
                        .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                        .map(this::toDto)
                        .orElse(null));

        return BlockchainAnchorStatusResponse.builder()
                .evidenceId(evidenceId)
                .evidenceHashAnchor(evidenceAnchor)
                .reportHashAnchors(reportAnchors)
                .latestMerkleRootAnchor(latestMerkle)
                .build();
    }

    @Transactional(readOnly = true)
    public BlockchainInfoDto getEvidenceBlockchainInfo(Evidence evidence) {
        if (evidence == null || evidence.getEvidenceId() == null) {
            return notAnchoredInfo();
        }
        return anchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        evidence.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                )
                .map(anchor -> toInfoDto(anchor, evidence))
                .orElseGet(this::notAnchoredInfo);
    }

    /** RQ-CMP-103: compare uses registered hash from blockchain_anchors.subject_hash */
    @Transactional(readOnly = true)
    public Optional<String> findAnchoredEvidenceSubjectHash(Long evidenceId) {
        if (evidenceId == null) {
            return Optional.empty();
        }
        return anchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(evidenceId, BlockchainAnchorType.EVIDENCE_HASH)
                .filter(anchor -> anchor.getStatus() == BlockchainAnchorStatus.ANCHORED)
                .map(BlockchainAnchor::getSubjectHash);
    }

    private BlockchainInfoDto notAnchoredInfo() {
        return BlockchainInfoDto.builder()
                .status("NOT_ANCHORED")
                .anchorType(BlockchainAnchorType.EVIDENCE_HASH.name())
                .hashValid(null)
                .verificationMessage("블록체인에 등록된 원본 해시 앵커가 없습니다.")
                .build();
    }

    private BlockchainAnchor persistFailedWithoutTx(
            BlockchainAnchorType anchorType,
            String subjectHash,
            Long evidenceId,
            Long reportId,
            Long userId,
            LocalDate merkleBatchDate,
            Integer merkleLeafCount,
            String signature,
            String signerCertHash,
            Boolean certVerified,
            String offchainLogHash,
            OffchainRef offchainRef,
            BlockchainAnchorRequest.AnalysisModelRef analysisModel,
            List<BlockchainAnchorRequest.AnalysisModuleRef> analysisModules,
            String errorCode,
            String errorMessage
    ) {
        BlockchainAnchor anchor = newPendingAnchor(
                anchorType,
                subjectHash,
                evidenceId,
                reportId,
                userId,
                merkleBatchDate,
                merkleLeafCount,
                signature,
                signerCertHash,
                certVerified,
                offchainLogHash,
                offchainRef,
                analysisModel,
                analysisModules
        );
        anchor.setStatus(BlockchainAnchorStatus.FAILED);
        anchor.setErrorCode(errorCode);
        anchor.setErrorMessage(errorMessage);
        log.warn("Blockchain anchor held type={} evidenceId={} errorCode={}",
                anchorType, evidenceId, errorCode);
        return anchorRepository.save(anchor);
    }

    private BlockchainAnchor executeAnchor(BlockchainAnchorRequest request, Long userId) {
        LocalDate merkleBatchDate = request.merkleBatchDate() == null
                ? null
                : LocalDate.parse(request.merkleBatchDate());
        BlockchainAnchor anchor = newPendingAnchor(
                request.anchorType(),
                request.subjectHash(),
                request.evidenceId(),
                request.reportId(),
                userId,
                merkleBatchDate,
                request.merkleLeafCount(),
                request.signature(),
                request.signerCertHash(),
                request.certVerified(),
                request.offchainLogHash(),
                request.offchainRef(),
                request.analysisModel(),
                request.analysisModules()
        );
        anchorRepository.save(anchor);

        BlockchainAnchorResult result = anchorClient.anchor(request);
        if (result.success()) {
            anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
            anchor.setTransactionHash(result.transactionHash());
            anchor.setBlockNumber(result.blockNumber());
            anchor.setAnchoredAt(LocalDateTime.now());
            notifyIfNeeded(anchor, userId);
        } else {
            anchor.setStatus(BlockchainAnchorStatus.FAILED);
            anchor.setErrorCode("FABRIC_SUBMIT_FAILED");
            anchor.setErrorMessage(result.errorMessage());
            log.warn("Blockchain anchor failed type={} subjectHash={} error={}",
                    request.anchorType(), request.subjectHash(), result.errorMessage());
        }

        return anchorRepository.save(anchor);
    }

    private BlockchainAnchor newPendingAnchor(
            BlockchainAnchorType anchorType,
            String subjectHash,
            Long evidenceId,
            Long reportId,
            Long userId,
            LocalDate merkleBatchDate,
            Integer merkleLeafCount,
            String signature,
            String signerCertHash,
            Boolean certVerified,
            String offchainLogHash,
            OffchainRef offchainRef,
            BlockchainAnchorRequest.AnalysisModelRef analysisModel,
            List<BlockchainAnchorRequest.AnalysisModuleRef> analysisModules
    ) {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(anchorType);
        anchor.setSubjectHash(subjectHash);
        anchor.setEvidenceId(evidenceId);
        anchor.setReportId(reportId);
        anchor.setCreatedBy(userId);
        anchor.setMerkleBatchDate(merkleBatchDate);
        anchor.setMerkleLeafCount(merkleLeafCount);
        anchor.setStatus(BlockchainAnchorStatus.PENDING);
        anchor.setNetwork(properties.getNetwork());
        anchor.setCreatedAt(LocalDateTime.now());
        anchor.setSignatureValue(signature);
        anchor.setSignerCertificateHash(signerCertHash);
        anchor.setCertVerified(certVerified);
        anchor.setOffchainLogHash(offchainLogHash);
        anchor.setOffchainRefJson(toOffchainRefJson(offchainRef));
        anchor.setAnalysisModelJson(toJson(analysisModel));
        anchor.setAnalysisModulesJson(toJson(analysisModules == null || analysisModules.isEmpty() ? null : analysisModules));
        return anchor;
    }

    private String toOffchainRefJson(OffchainRef offchainRef) {
        if (offchainRef == null || offchainRef.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(offchainRef.toMap());
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize offchainRef", ex);
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize blockchain anchor metadata", ex);
            return null;
        }
    }

    private AnalysisAnchorMetadata buildAnalysisAnchorMetadata(Report report) {
        if (report == null || report.getAnalysisResultId() == null) {
            return AnalysisAnchorMetadata.empty();
        }
        List<com.example.demo.domain.AnalysisModuleResult> modules =
                analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(report.getAnalysisResultId());
        if (modules.isEmpty()) {
            return AnalysisAnchorMetadata.empty();
        }

        // REPORT_HASH snapshot: primary fusion (deepfake) + all module versions,
        // including forgery_spatial (TruFor) / forgery_temporal (TimeSformer-Forgery)
        // when GPU FORGERY_ENABLED lane persists modelScores.
        BlockchainAnchorRequest.AnalysisModelRef analysisModel = null;
        List<BlockchainAnchorRequest.AnalysisModuleRef> analysisModules = new ArrayList<>();
        for (com.example.demo.domain.AnalysisModuleResult module : modules) {
            String moduleName = normalize(module.getModuleName());
            String modelName = normalize(module.getModelName());
            String modelVersion = normalize(module.getModelVersion());
            if (moduleName == null || modelName == null || modelVersion == null) {
                continue;
            }
            if ("video_timeline".equalsIgnoreCase(moduleName)) {
                if (analysisModel == null) {
                    analysisModel = new BlockchainAnchorRequest.AnalysisModelRef(
                            modelName,
                            modelVersion,
                            modelVersion
                    );
                }
                continue;
            }
            if ("deepfake".equalsIgnoreCase(moduleName) && analysisModel == null) {
                analysisModel = new BlockchainAnchorRequest.AnalysisModelRef(
                        modelName,
                        modelVersion,
                        modelVersion
                );
            }
            analysisModules.add(new BlockchainAnchorRequest.AnalysisModuleRef(
                    moduleName,
                    modelName,
                    modelVersion
            ));
        }
        return new AnalysisAnchorMetadata(
                analysisModel,
                analysisModules.isEmpty() ? List.of() : List.copyOf(analysisModules)
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record AnalysisAnchorMetadata(
            BlockchainAnchorRequest.AnalysisModelRef analysisModel,
            List<BlockchainAnchorRequest.AnalysisModuleRef> analysisModules
    ) {
        private static AnalysisAnchorMetadata empty() {
            return new AnalysisAnchorMetadata(null, List.of());
        }
    }

    private void notifyIfNeeded(BlockchainAnchor anchor, Long userId) {
        if (userId == null || anchor.getAnchorType() == BlockchainAnchorType.MERKLE_ROOT) {
            return;
        }
        notificationService.notifyBlockchainAnchored(
                userId,
                anchor.getEvidenceId(),
                anchor.getAnchorType(),
                anchor.getTransactionHash()
        );
    }

    private BlockchainAnchorRecordDto toDto(BlockchainAnchor anchor) {
        return BlockchainAnchorRecordDto.builder()
                .anchorId(anchor.getAnchorId())
                .anchorType(anchor.getAnchorType())
                .status(anchor.getStatus())
                .subjectHash(anchor.getSubjectHash())
                .transactionHash(anchor.getTransactionHash())
                .blockNumber(anchor.getBlockNumber())
                .network(anchor.getNetwork())
                .anchoredAt(ApiDateTimeFormatter.formatUtc(anchor.getAnchoredAt()))
                .evidenceId(anchor.getEvidenceId())
                .reportId(anchor.getReportId())
                .merkleBatchDate(anchor.getMerkleBatchDate() == null
                        ? null
                        : anchor.getMerkleBatchDate().toString())
                .merkleLeafCount(anchor.getMerkleLeafCount())
                .signature(anchor.getSignatureValue())
                .signerCertHash(anchor.getSignerCertificateHash())
                .certVerified(anchor.getCertVerified())
                .offchainLogHash(anchor.getOffchainLogHash())
                .offchainRefJson(anchor.getOffchainRefJson())
                .analysisModelJson(anchor.getAnalysisModelJson())
                .analysisModulesJson(anchor.getAnalysisModulesJson())
                .errorCode(anchor.getErrorCode())
                .message(resolveMessage(anchor))
                .transactionExplorerUrl(buildTransactionExplorerUrl(anchor.getTransactionHash()))
                .build();
    }

    private BlockchainInfoDto toInfoDto(BlockchainAnchor anchor, Evidence evidence) {
        if (anchor.getStatus() == BlockchainAnchorStatus.FAILED
                && ERROR_MANIFEST_SIGNATURE_INVALID.equals(anchor.getErrorCode())) {
            return BlockchainInfoDto.builder()
                    .status(anchor.getStatus().name())
                    .anchorType(anchor.getAnchorType().name())
                    .subjectHash(anchor.getSubjectHash())
                    .network(anchor.getNetwork())
                    .hashValid(true)
                    .certVerified(false)
                    .errorCode(anchor.getErrorCode())
                    .verificationMessage(
                            "원본 해시는 저장되었으나 매니페스트 서명 검증 실패로 블록체인 앵커가 보류되었습니다.")
                    .build();
        }

        BlockchainHashIntegrityEvaluator.HashIntegrityResult integrity =
                BlockchainHashIntegrityEvaluator.evaluate(evidence, anchor);
        return BlockchainInfoDto.builder()
                .status(anchor.getStatus().name())
                .anchorType(anchor.getAnchorType().name())
                .subjectHash(anchor.getSubjectHash())
                .transactionHash(anchor.getTransactionHash())
                .anchoredAt(ApiDateTimeFormatter.formatUtc(anchor.getAnchoredAt()))
                .network(anchor.getNetwork())
                .hashValid(integrity.hashValid())
                .certVerified(anchor.getCertVerified())
                .errorCode(anchor.getErrorCode())
                .verificationMessage(integrity.verificationMessage())
                .transactionExplorerUrl(buildTransactionExplorerUrl(anchor.getTransactionHash()))
                .build();
    }

    private String buildTransactionExplorerUrl(String transactionHash) {
        if (transactionHash == null || transactionHash.isBlank()) {
            return null;
        }
        String template = properties.getExplorerUrlTemplate();
        if (template == null || template.isBlank()) {
            return null;
        }
        return template.replace("{txHash}", transactionHash);
    }

    private String resolveMessage(BlockchainAnchor anchor) {
        return switch (anchor.getStatus()) {
            case ANCHORED -> "블록체인 앵커링이 완료되었습니다.";
            case PENDING -> "블록체인 앵커링이 진행 중입니다.";
            case FAILED -> {
                if (ERROR_MANIFEST_SIGNATURE_INVALID.equals(anchor.getErrorCode())) {
                    yield "매니페스트 서명 검증 실패로 블록체인 앵커가 보류되었습니다.";
                }
                yield anchor.getErrorMessage() == null
                        ? "블록체인 앵커링에 실패했습니다."
                        : anchor.getErrorMessage();
            }
        };
    }
}
