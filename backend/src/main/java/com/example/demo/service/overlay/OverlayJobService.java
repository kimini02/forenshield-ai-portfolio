package com.example.demo.service.overlay;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.OverlayJob;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.domain.enums.OverlayJobStatus;
import com.example.demo.dto.OverlayJobMessage;
import com.example.demo.dto.OverlayJobStatusResponse;
import com.example.demo.dto.OverlayResultMessage;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.OverlayJobRepository;
import com.example.demo.service.analysis.S3AnalysisAccessService;
import com.example.demo.service.analysis.VideoAnalysisDetailsBuilder;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.util.ApiDateTimeFormatter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverlayJobService {

    private static final Set<String> SUPPORTED_MODULES = Set.of(
            "cnn",
            "temporal",
            "optical",
            "forgery_spatial"
    );

    private static final Set<OverlayJobStatus> ACTIVE_STATUSES = Set.of(
            OverlayJobStatus.QUEUED,
            OverlayJobStatus.PROCESSING
    );

    private final EvidenceAccessService evidenceAccessService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final OverlayJobRepository overlayJobRepository;
    private final OverlayJobEnqueuer overlayJobEnqueuer;
    private final S3AnalysisAccessService s3AnalysisAccessService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public OverlayJobStatusResponse generate(User user, Long evidenceId, String module) {
        try {
            return doGenerate(user, evidenceId, module);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Overlay DB error evidenceId={} module={}", evidenceId, module, ex);
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OVERLAY_DB_UNAVAILABLE",
                    "오버레이 작업 저장에 실패했습니다. 관리자에게 문의해주세요."
            );
        } catch (Exception ex) {
            log.error("Overlay generate failed evidenceId={} module={}", evidenceId, module, ex);
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OVERLAY_UNAVAILABLE",
                    "오버레이 생성을 시작하지 못했습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    private OverlayJobStatusResponse doGenerate(User user, Long evidenceId, String module) {
        String normalizedModule = normalizeModule(module);
        Evidence evidence = evidenceAccessService.requireReadable(user, evidenceId);

        AnalysisRequest analysisRequest = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow(() -> new IllegalStateException("분석 결과가 없어 오버레이를 생성할 수 없습니다."));
        if (analysisRequest.getStatus() != AnalysisStatus.COMPLETED) {
            throw new IllegalStateException("분석이 완료된 증거만 오버레이를 생성할 수 있습니다.");
        }

        Long analysisRequestId = analysisRequest.getAnalysisRequestId();

        // Scope reuse to this analysis only — re-analysis must not return an older MP4 job.
        OverlayJob existing = overlayJobRepository
                .findFirstByEvidenceIdAndAnalysisRequestIdAndModuleAndStatusInOrderByRequestedAtDesc(
                        evidenceId,
                        analysisRequestId,
                        normalizedModule,
                        ACTIVE_STATUSES
                )
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        OverlayJob completed = overlayJobRepository
                .findFirstByEvidenceIdAndAnalysisRequestIdAndModuleAndStatusInOrderByRequestedAtDesc(
                        evidenceId,
                        analysisRequestId,
                        normalizedModule,
                        List.of(OverlayJobStatus.COMPLETED)
                )
                .orElse(null);
        if (completed != null && completed.getOverlayVideoUrl() != null && !completed.getOverlayVideoUrl().isBlank()) {
            return toResponse(completed);
        }

        Map<String, Object> timelineDetails = loadTimelineDetails(analysisRequestId);
        OverlayJobMessage messagePayload = buildJobMessage(
                evidence,
                analysisRequest,
                normalizedModule,
                timelineDetails
        );

        OverlayJob job = new OverlayJob();
        job.setEvidenceId(evidenceId);
        job.setAnalysisRequestId(analysisRequestId);
        job.setModule(normalizedModule);
        job.setStatus(OverlayJobStatus.QUEUED);
        job.setProgressPercent(0);
        job.setRequestedBy(user.getUserId());
        job.setRequestedAt(LocalDateTime.now());
        job = overlayJobRepository.save(job);

        messagePayload.setOverlayJobId(job.getOverlayJobId());
        messagePayload.setRequestedAt(ApiDateTimeFormatter.formatUtc(job.getRequestedAt()));

        try {
            overlayJobEnqueuer.enqueue(messagePayload);
        } catch (Exception ex) {
            log.error("Failed to enqueue overlay job {}", job.getOverlayJobId(), ex);
            job.setStatus(OverlayJobStatus.FAILED);
            job.setErrorCode("OVERLAY_ENQUEUE_FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            job.setProgressPercent(100);
        }

        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public OverlayJobStatusResponse getStatus(User user, Long evidenceId, Long overlayJobId) {
        evidenceAccessService.requireReadable(user, evidenceId);
        OverlayJob job = overlayJobRepository.findById(overlayJobId)
                .orElseThrow(() -> new IllegalArgumentException("오버레이 작업을 찾을 수 없습니다."));
        if (!Objects.equals(job.getEvidenceId(), evidenceId)) {
            throw new IllegalArgumentException("오버레이 작업이 해당 증거에 속하지 않습니다.");
        }
        return toResponse(job);
    }

    public void applyOverlayResult(OverlayResultMessage response) {
        if (response == null || response.getOverlayJobId() == null) {
            log.warn("Ignored overlay result without overlayJobId");
            return;
        }

        String status = response.getStatus() == null ? "" : response.getStatus().trim().toUpperCase(Locale.ROOT);
        if ("IN_PROGRESS".equals(status) || "PROCESSING".equals(status) || "ANALYZING".equals(status)) {
            int progress = normalizeProgress(response.getProgressPercent());
            if (progress >= 0) {
                updateProgress(response.getOverlayJobId(), Math.min(99, progress), response.getMessage());
            }
            return;
        }

        if ("FAILED".equals(status)) {
            finalizeFailed(
                    response.getOverlayJobId(),
                    response.getErrorCode() == null ? "OVERLAY_FAILED" : response.getErrorCode(),
                    response.getMessage()
            );
            return;
        }

        if (!"COMPLETED".equals(status)) {
            log.warn("Ignored overlay result with unsupported status={}", response.getStatus());
            return;
        }

        finalizeCompleted(response);
    }

    private void updateProgress(Long overlayJobId, int progressPercent, String message) {
        transactionTemplate.executeWithoutResult(status ->
                overlayJobRepository.findById(overlayJobId).ifPresent(job -> {
                    if (job.getStatus() == OverlayJobStatus.COMPLETED || job.getStatus() == OverlayJobStatus.FAILED) {
                        return;
                    }
                    if (job.getStatus() == OverlayJobStatus.QUEUED) {
                        job.setStatus(OverlayJobStatus.PROCESSING);
                        job.setStartedAt(LocalDateTime.now());
                    }
                    int next = Math.max(job.getProgressPercent(), Math.min(99, progressPercent));
                    job.setProgressPercent(next);
                    if (message != null && !message.isBlank()) {
                        job.setErrorMessage(null);
                    }
                })
        );
    }

    private void finalizeFailed(Long overlayJobId, String errorCode, String message) {
        transactionTemplate.executeWithoutResult(status ->
                overlayJobRepository.findById(overlayJobId).ifPresent(job -> {
                    job.setStatus(OverlayJobStatus.FAILED);
                    job.setProgressPercent(100);
                    job.setErrorCode(errorCode);
                    job.setErrorMessage(message);
                    job.setCompletedAt(LocalDateTime.now());
                })
        );
    }

    private void finalizeCompleted(OverlayResultMessage response) {
        transactionTemplate.executeWithoutResult(status -> {
            OverlayJob job = overlayJobRepository.findById(response.getOverlayJobId()).orElse(null);
            if (job == null) {
                return;
            }
            String url = response.getOverlayVideoUrl();
            if (url == null || url.isBlank()) {
                job.setStatus(OverlayJobStatus.FAILED);
                job.setProgressPercent(100);
                job.setErrorCode("OVERLAY_EMPTY");
                job.setErrorMessage(response.getMessage() == null ? "오버레이 URL이 비어 있습니다." : response.getMessage());
                job.setCompletedAt(LocalDateTime.now());
                return;
            }

            job.setStatus(OverlayJobStatus.COMPLETED);
            job.setProgressPercent(100);
            job.setOverlayVideoUrl(url);
            job.setErrorCode(null);
            job.setErrorMessage(null);
            job.setCompletedAt(LocalDateTime.now());
            if (job.getStartedAt() == null) {
                job.setStartedAt(job.getCompletedAt());
            }

            patchTimelineOverlayUrl(job.getAnalysisRequestId(), job.getModule(), url);
        });
    }

    private void patchTimelineOverlayUrl(Long analysisRequestId, String module, String overlayVideoUrl) {
        AnalysisResult result = analysisResultRepository
                .findByAnalysisRequestId(analysisRequestId)
                .orElse(null);
        if (result == null) {
            log.warn("No analysis result for overlay patch analysisRequestId={}", analysisRequestId);
            return;
        }

        List<AnalysisModuleResult> modules = analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());
        AnalysisModuleResult timeline = modules.stream()
                .filter(m -> VideoAnalysisDetailsBuilder.MODULE_VIDEO_TIMELINE.equals(m.getModuleName()))
                .findFirst()
                .orElse(null);
        if (timeline == null || timeline.getDetailsJson() == null) {
            log.warn("No video_timeline module for overlay patch analysisRequestId={}", analysisRequestId);
            return;
        }

        try {
            Map<String, Object> details = objectMapper.readValue(
                    timeline.getDetailsJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {}
            );

            String artifactKey = artifactKeyForModule(module);
            List<Map<String, Object>> artifacts = castMapList(details.get("modelOverlayArtifacts"));
            boolean foundArtifact = false;
            for (Map<String, Object> artifact : artifacts) {
                if (artifactKey.equals(String.valueOf(artifact.get("key")))) {
                    artifact.put("overlayVideoUrl", overlayVideoUrl);
                    artifact.put("status", "ready");
                    foundArtifact = true;
                    break;
                }
            }
            if (!foundArtifact) {
                Map<String, Object> artifact = new LinkedHashMap<>();
                artifact.put("key", artifactKey);
                artifact.put("category", module.startsWith("forgery") ? "forgery" : "deepfake");
                artifact.put("label", labelForModule(module));
                artifact.put("overlayVideoUrl", overlayVideoUrl);
                artifact.put("status", "ready");
                artifacts.add(artifact);
            }
            details.put("modelOverlayArtifacts", artifacts);

            if ("cnn".equals(module)) {
                details.put("overlayVideoUrl", overlayVideoUrl);
            }

            List<Map<String, Object>> timelines = castMapList(details.get("moduleTimelines"));
            for (Map<String, Object> timelineRow : timelines) {
                if (module.equals(String.valueOf(timelineRow.get("module")))) {
                    timelineRow.put("overlayVideoUrl", overlayVideoUrl);
                }
            }
            details.put("moduleTimelines", timelines);

            timeline.setDetailsJson(objectMapper.writeValueAsString(details));
            analysisModuleResultRepository.save(timeline);
        } catch (Exception ex) {
            log.error("Failed to patch timeline overlay URL analysisRequestId={}", analysisRequestId, ex);
        }
    }

    private OverlayJobMessage buildJobMessage(
            Evidence evidence,
            AnalysisRequest analysisRequest,
            String module,
            Map<String, Object> timelineDetails
    ) {
        String videoKey = resolveOverlayVideoKey(evidence);
        String downloadUrl = s3AnalysisAccessService.createGpuDownloadUrl(videoKey);

        OverlayJobMessage.OverlayJobMessageBuilder builder = OverlayJobMessage.builder()
                .jobType("OVERLAY")
                .analysisRequestId(analysisRequest.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .module(module)
                .filePath(videoKey)
                .s3ObjectKey(videoKey)
                .s3Bucket(s3AnalysisAccessService.getEvidenceBucket())
                .s3Region(s3AnalysisAccessService.getAwsRegion())
                .presignedDownloadUrl(downloadUrl);

        if ("temporal".equals(module)) {
            builder.clipRisks(readClipRisks(timelineDetails, module));
        } else if ("optical".equals(module)) {
            builder.pairRisks(readPairRisks(timelineDetails, module));
        } else {
            builder.frameRisks(readFrameRisks(timelineDetails, module));
        }

        return builder.build();
    }

    /** Analysis deletes the S3 copy after COMPLETED; on-demand overlay must use original. */
    private String resolveOverlayVideoKey(Evidence evidence) {
        if (evidence.getCopyStatus() == CopyStatus.ACTIVE) {
            String copyKey = evidence.getCopyStoragePath();
            if (copyKey != null && !copyKey.isBlank()) {
                return copyKey;
            }
        }
        String original = evidence.getOriginalStoragePath();
        if (original == null || original.isBlank()) {
            throw new IllegalStateException("원본 영상 경로가 없습니다.");
        }
        return original;
    }

    private List<OverlayJobMessage.FrameRiskItem> readFrameRisks(Map<String, Object> details, String module) {
        List<Map<String, Object>> rows = moduleTimelineRiskMaps(details, module, "frameRisks");
        if (rows.isEmpty() && "cnn".equals(module)) {
            rows = castMapList(details.get("frameRisks"));
        }
        List<OverlayJobMessage.FrameRiskItem> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(OverlayJobMessage.FrameRiskItem.builder()
                    .frameIndex(asInt(row.get("frameIndex")))
                    .timestampSec(asDouble(row.get("timestampSec")))
                    .riskScore(asDouble(row.get("riskScore")))
                    .build());
        }
        return out;
    }

    private List<OverlayJobMessage.ClipRiskItem> readClipRisks(Map<String, Object> details, String module) {
        List<Map<String, Object>> rows = moduleTimelineRiskMaps(details, module, "clipRisks");
        if (rows.isEmpty()) {
            rows = castMapList(details.get("clipRisks"));
        }
        List<OverlayJobMessage.ClipRiskItem> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(OverlayJobMessage.ClipRiskItem.builder()
                    .clipIndex(asInt(row.get("clipIndex")))
                    .startFrameIndex(asInt(row.get("startFrameIndex")))
                    .endFrameIndex(asInt(row.get("endFrameIndex")))
                    .startTimeSec(asDouble(row.get("startTimeSec")))
                    .endTimeSec(asDouble(row.get("endTimeSec")))
                    .riskScore(asDouble(row.get("riskScore")))
                    .build());
        }
        return out;
    }

    private List<OverlayJobMessage.PairRiskItem> readPairRisks(Map<String, Object> details, String module) {
        List<Map<String, Object>> rows = moduleTimelineRiskMaps(details, module, "pairRisks");
        if (rows.isEmpty()) {
            rows = castMapList(details.get("pairRisks"));
        }
        List<OverlayJobMessage.PairRiskItem> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(OverlayJobMessage.PairRiskItem.builder()
                    .pairIndex(asInt(row.get("pairIndex")))
                    .frameIndexA(asInt(row.get("frameIndexA")))
                    .frameIndexB(asInt(row.get("frameIndexB")))
                    .timestampSec(asDouble(row.get("timestampSec")))
                    .riskScore(asDouble(row.get("riskScore")))
                    .motionMagnitude(asDouble(row.get("motionMagnitude")))
                    .build());
        }
        return out;
    }

    private List<Map<String, Object>> moduleTimelineRiskMaps(
            Map<String, Object> details,
            String module,
            String riskKey
    ) {
        for (Map<String, Object> timeline : castMapList(details.get("moduleTimelines"))) {
            if (module.equals(String.valueOf(timeline.get("module")))) {
                return castMapList(timeline.get(riskKey));
            }
        }
        return List.of();
    }

    private Map<String, Object> loadTimelineDetails(Long analysisRequestId) {
        AnalysisResult result = analysisResultRepository
                .findByAnalysisRequestId(analysisRequestId)
                .orElseThrow(() -> new IllegalStateException("분석 결과 상세가 없습니다."));
        return analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId())
                .stream()
                .filter(m -> VideoAnalysisDetailsBuilder.MODULE_VIDEO_TIMELINE.equals(m.getModuleName()))
                .findFirst()
                .map(m -> {
                    try {
                        if (m.getDetailsJson() == null || m.getDetailsJson().isBlank()) {
                            return Map.<String, Object>of();
                        }
                        return objectMapper.readValue(
                                m.getDetailsJson(),
                                new TypeReference<LinkedHashMap<String, Object>>() {}
                        );
                    } catch (Exception ex) {
                        throw new IllegalStateException("타임라인 상세를 읽지 못했습니다.", ex);
                    }
                })
                .orElse(Map.of());
    }

    private static String normalizeModule(String module) {
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("module이 필요합니다.");
        }
        String normalized = module.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("deepfake:")) {
            normalized = normalized.substring("deepfake:".length());
        }
        if (normalized.startsWith("forgery:")) {
            normalized = normalized.substring("forgery:".length());
        }
        if (!SUPPORTED_MODULES.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 오버레이 모듈입니다: " + module);
        }
        return normalized;
    }

    private static String artifactKeyForModule(String module) {
        return switch (module) {
            case "cnn" -> "deepfake:cnn";
            case "temporal" -> "deepfake:temporal";
            case "optical" -> "deepfake:optical";
            case "forgery_spatial" -> "forgery:forgery_spatial";
            default -> module;
        };
    }

    private static String labelForModule(String module) {
        return switch (module) {
            case "cnn" -> "Xception";
            case "temporal" -> "TimeSformer";
            case "optical" -> "GMFlow";
            case "forgery_spatial" -> "TruFor";
            default -> module;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return out;
    }

    private static Integer asInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int normalizeProgress(Integer progressPercent) {
        if (progressPercent == null) {
            return -1;
        }
        return Math.max(0, Math.min(100, progressPercent));
    }

    private static OverlayJobStatusResponse toResponse(OverlayJob job) {
        return OverlayJobStatusResponse.builder()
                .overlayJobId(job.getOverlayJobId())
                .evidenceId(job.getEvidenceId())
                .module(job.getModule())
                .status(job.getStatus().name())
                .progressPercent(job.getProgressPercent())
                .overlayVideoUrl(job.getOverlayVideoUrl())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
