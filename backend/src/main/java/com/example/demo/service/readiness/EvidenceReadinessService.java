package com.example.demo.service.readiness;

import com.example.demo.config.ReadinessProperties;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.ReadinessSource;
import com.example.demo.dto.readiness.EvidenceReadinessResponse;
import com.example.demo.dto.readiness.ReadinessSnapshot;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

// 총괄
// readiness 비즈니스 총괄 (저장·조회·프레임 검사)
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceReadinessService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final ReadinessEvaluator readinessEvaluator;
    private final VideoReadinessRunner videoReadinessRunner;
    private final EvidenceReadinessFileService evidenceReadinessFileService;
    private final ReadinessProperties readinessProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReadinessSnapshot seedFfprobeReadiness(Long evidenceId) { // 업로드 직후 ffprobe 메타만으로 즉시 판정
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId)
                .orElse(null);
        ReadinessSnapshot snapshot = readinessEvaluator.evaluateFromFfprobe(metadata);
        if (metadata != null) {
            persistSnapshot(metadata, snapshot);
        }
        return snapshot;
    }

    @Transactional(readOnly = true)
    // DB 스냅샷 조회 → API 응답
    public EvidenceReadinessResponse getReadiness(User user, Long evidenceId) {
        Evidence evidence = requireOwnedEvidence(user, evidenceId);
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        ReadinessSnapshot snapshot = parseSnapshot(metadata != null ? metadata.getReadinessJson() : null);
        if (snapshot == null) {
            snapshot = readinessEvaluator.evaluateFromFfprobe(metadata);
        }
        return toResponse(evidence.getEvidenceId(), snapshot);
    }

    //ReadinessSnapshot → EvidenceReadinessResponse 변환
    @Transactional
    public EvidenceReadinessResponse runFrameReadinessCheck(User user, Long evidenceId) {
        Evidence evidence = requireOwnedEvidence(user, evidenceId);
        if (evidence.getFileType() != FileType.VIDEO) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "READINESS_VIDEO_ONLY",
                    "영상 파일만 프레임 기반 화질 검사를 수행할 수 있습니다.");
        }

        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "METADATA_NOT_FOUND", "증거 메타데이터를 찾을 수 없습니다."));

        if (!readinessProperties.isFrameSamplingConfigured()) {
            ReadinessSnapshot fallback = readinessEvaluator.evaluateFromFfprobe(metadata);
            fallback = fallback.toBuilder()
                    .frameCheckStatus("SKIPPED")
                    .frameCheckMessage("프레임 샘플링이 비활성화되었거나 readiness.script-path 가 설정되지 않았습니다.")
                    .build();
            persistSnapshot(metadata, fallback);
            return toResponse(evidenceId, fallback);
        }

        if (!readinessProperties.isScriptPresent()) {
            ReadinessSnapshot fallback = readinessEvaluator.evaluateFromFfprobe(metadata);
            fallback = fallback.toBuilder()
                    .frameCheckStatus("SKIPPED")
                    .frameCheckMessage("readiness 스크립트가 컨테이너에 없습니다. backend 이미지 배포를 확인하세요.")
                    .build();
            persistSnapshot(metadata, fallback);
            return toResponse(evidenceId, fallback);
        }

        Path localFile = null;
        try {
            localFile = evidenceReadinessFileService.downloadOriginal(evidence);
            ReadinessSnapshot snapshot = videoReadinessRunner.run(localFile);
            if (snapshot.getVideoMetadata() == null) {
                snapshot = snapshot.toBuilder()
                        .videoMetadata(readinessEvaluator.evaluateFromFfprobe(metadata).getVideoMetadata())
                        .build();
            }
            snapshot = snapshot.toBuilder()
                    .source(ReadinessSource.FRAME_SAMPLE)
                    .frameCheckStatus("COMPLETED")
                    .build();
            persistSnapshot(metadata, snapshot);
            return toResponse(evidenceId, snapshot);
        } catch (Exception ex) {
            log.warn("Frame readiness check failed evidenceId={}: {}", evidenceId, ex.getMessage());
            ReadinessSnapshot failed = readinessEvaluator.evaluateFromFfprobe(metadata).toBuilder()
                    .source(ReadinessSource.FFPROBE)
                    .frameCheckStatus("FAILED")
                    .frameCheckMessage(ex.getMessage())
                    .build();
            persistSnapshot(metadata, failed);
            return toResponse(evidenceId, failed);
        } finally {
            evidenceReadinessFileService.deleteQuietly(localFile);
        }
    }

    @Transactional(readOnly = true)
    public ReadinessSnapshot resolveStoredSnapshot(Long evidenceId) {
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        ReadinessSnapshot snapshot = parseSnapshot(metadata != null ? metadata.getReadinessJson() : null);
        if (snapshot == null) {
            snapshot = readinessEvaluator.evaluateFromFfprobe(metadata);
        }
        return snapshot;
    }

    public void assertQualityAcknowledged(Long evidenceId, Boolean acknowledgeQualityWarning) {
        ReadinessSnapshot snapshot = resolveStoredSnapshot(evidenceId);
        if (snapshot != null
                && snapshot.isRequiresAcknowledgement()
                && !Boolean.TRUE.equals(acknowledgeQualityWarning)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "QUALITY_WARNING_REQUIRED",
                    "화질이 분석에 적합하지 않을 수 있습니다. 안내를 확인한 후 계속 진행하려면 acknowledgeQualityWarning=true 로 요청해 주세요.");
        }
    }

    public EvidenceReadinessResponse toResponse(Long evidenceId, ReadinessSnapshot snapshot) {
        if (snapshot == null) {
            return EvidenceReadinessResponse.builder()
                    .evidenceId(evidenceId)
                    .readinessTier(com.example.demo.domain.enums.ReadinessTier.BLOCK)
                    .confidenceCap(60)
                    .requiresAcknowledgement(false)
                    .reasons(java.util.List.of("readiness 정보 없음"))
                    .build();
        }
        return EvidenceReadinessResponse.builder()
                .evidenceId(evidenceId)
                .source(snapshot.getSource())
                .checkedAt(snapshot.getCheckedAt())
                .readinessTier(snapshot.getReadinessTier())
                .confidenceCap(snapshot.getConfidenceCap())
                .reasons(snapshot.getReasons())
                .requiresAcknowledgement(snapshot.isRequiresAcknowledgement())
                .thresholdsVersion(snapshot.getThresholdsVersion())
                .videoMetadata(snapshot.getVideoMetadata())
                .frameMetrics(snapshot.getFrameMetrics())
                .spatial(snapshot.getSpatial())
                .frameCheckStatus(snapshot.getFrameCheckStatus())
                .frameCheckMessage(snapshot.getFrameCheckMessage())
                .build();
    }

    private Evidence requireOwnedEvidence(User user, Long evidenceId) {
        return evidenceRepository.findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));
    }

    private void persistSnapshot(EvidenceMetadata metadata, ReadinessSnapshot snapshot) {
        try {
            metadata.setReadinessJson(objectMapper.writeValueAsString(snapshot));
            evidenceMetadataRepository.save(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize readiness snapshot", ex);
        }
    }

    private ReadinessSnapshot parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReadinessSnapshot.class);
        } catch (JsonProcessingException ex) {
            log.warn("Invalid readiness_json, will recompute: {}", ex.getMessage());
            return null;
        }
    }
}
