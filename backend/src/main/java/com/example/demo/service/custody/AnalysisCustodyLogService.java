package com.example.demo.service.custody;

import com.example.demo.config.AnalysisMessagingProperties;
import com.example.demo.config.RabbitMqConfig;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.dto.readiness.ReadinessSnapshot;
import com.example.demo.util.JsonPayloadWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalysisCustodyLogService {

    public static final String STEP_COPY_CREATED = "ANALYSIS_COPY_CREATED";
    public static final String STEP_COPY_VERIFIED = "ANALYSIS_COPY_VERIFIED";
    public static final String STEP_COPY_DELETED = "ANALYSIS_COPY_DELETED";

    public static final String ERR_COPY_CREATE = "ANALYSIS_COPY_CREATE_FAILED";
    public static final String ERR_COPY_VERIFY = "ANALYSIS_COPY_VERIFY_FAILED";
    public static final String ERR_COPY_DELETE = "ANALYSIS_COPY_DELETE_FAILED";

    public static final String RABBITMQ_PUBLISH_FAILED = "RABBITMQ_PUBLISH_FAILED";
    public static final String S3_COPY_NOT_READY = "S3_COPY_NOT_READY";
    public static final String RABBITMQ_PUBLISH_STEP = "RABBITMQ_PUBLISH";
    public static final String RABBITMQ_PUBLISH_FAILURE_MESSAGE = "분석 요청 큐 등록에 실패했습니다.";

    private final CustodyLogService custodyLogService;
    private final JsonPayloadWriter jsonPayloadWriter;
    private final AnalysisMessagingProperties messagingProperties;

    public void recordCopyCreated(Long actorId, Evidence evidence) {
        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ANALYSIS_COPY_CREATED",
                evidence.getCopyHashValue(),
                evidence.getCopyStoragePath(),
                "분석용 증거 사본 생성",
                jsonPayloadWriter.toJson(copyPayload(evidence, "ACTIVE")),
                null
        );
    }

    public void recordCopyVerified(Long actorId, Evidence evidence) {
        Map<String, Object> payload = copyPayload(evidence, evidence.getCopyStatus().name());
        payload.put("originalHashValue", evidence.getOriginalHashValue());
        payload.put("copyHashValue", evidence.getCopyHashValue());
        payload.put("verified", true);

        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ANALYSIS_COPY_VERIFIED",
                evidence.getCopyHashValue(),
                evidence.getCopyStoragePath(),
                "원본·사본 SHA-256 일치 검증 완료",
                jsonPayloadWriter.toJson(payload),
                null
        );
    }

    public void recordCopyDeleted(Long actorId, Evidence evidence, String deletedCopyPath) {
        Map<String, Object> payload = copyPayload(evidence, "DELETED");
        payload.put("deletedCopyPath", deletedCopyPath);

        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ANALYSIS_COPY_DELETED",
                evidence.getCopyHashValue(),
                deletedCopyPath,
                "분석용 증거 사본 삭제",
                jsonPayloadWriter.toJson(payload),
                null
        );
    }

    public void recordCopyError(
            Long actorId,
            Evidence evidence,
            String step,
            String errorCode,
            String message
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        payload.put("errorCode", errorCode);
        payload.put("message", message);
        payload.put("evidenceId", evidence.getEvidenceId());
        if (evidence.getCopyStoragePath() != null) {
            payload.put("copyStoragePath", evidence.getCopyStoragePath());
        }

        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ERROR_OCCURRED",
                evidence.getOriginalHashValue(),
                evidence.getCopyStoragePath() != null
                        ? evidence.getCopyStoragePath()
                        : evidence.getOriginalStoragePath(),
                message,
                jsonPayloadWriter.toJson(payload),
                null
        );
    }

    public void recordAnalysisStarted(AnalysisRequest request, Evidence evidence) {
        custodyLogService.record(
                request.getRequestedBy(),
                CustodyTargetType.ANALYSIS_REQUEST,
                request.getAnalysisRequestId(),
                "ANALYSIS_STARTED",
                subjectHash(evidence),
                storagePathForAnalysis(evidence),
                "AI 분석 시작",
                jsonPayloadWriter.toJson(analysisPayload(request, evidence, "ANALYZING")),
                null
        );
    }

    public void recordAnalysisCompleted(AnalysisRequest request, Evidence evidence, Long analysisResultId) {
        Map<String, Object> payload = analysisPayload(request, evidence, "COMPLETED");
        payload.put("analysisResultId", analysisResultId);

        custodyLogService.record(
                request.getRequestedBy(),
                CustodyTargetType.ANALYSIS_RESULT,
                analysisResultId,
                "ANALYSIS_COMPLETED",
                subjectHash(evidence),
                storagePathForAnalysis(evidence),
                "AI 분석 완료",
                jsonPayloadWriter.toJson(payload),
                null
        );
    }

    public void recordAnalysisFailed(AnalysisRequest request, Evidence evidence) {
        Map<String, Object> payload = analysisPayload(request, evidence, "FAILED");
        payload.put("errorCode", request.getErrorCode());
        payload.put("errorMessage", request.getErrorMessage());

        custodyLogService.record(
                request.getRequestedBy(),
                CustodyTargetType.ANALYSIS_REQUEST,
                request.getAnalysisRequestId(),
                "ANALYSIS_FAILED",
                subjectHash(evidence),
                storagePathForAnalysis(evidence),
                "AI 분석 실패",
                jsonPayloadWriter.toJson(payload),
                null
        );
    }

    public void recordQualityWarningAcknowledged(Long actorId, Evidence evidence, ReadinessSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("readinessTier", snapshot.getReadinessTier().name());
        payload.put("confidenceCap", snapshot.getConfidenceCap());
        payload.put("reasons", snapshot.getReasons());
        payload.put("requiresAcknowledgement", snapshot.isRequiresAcknowledgement());
        payload.put("acknowledgeQualityWarning", true);
        if (snapshot.getSource() != null) {
            payload.put("source", snapshot.getSource().name());
        }

        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "QUALITY_WARNING_ACKNOWLEDGED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                "화질 적합성 안내 확인 후 분석 진행",
                jsonPayloadWriter.toJson(payload),
                null
        );
    }

    public void recordAnalysisRequested(
            Long userId,
            Evidence evidence,
            AnalysisRequest savedRequest,
            String caseName,
            AnalysisJobMessage message
    ) {
        custodyLogService.record(
                userId,
                CustodyTargetType.ANALYSIS_REQUEST,
                savedRequest.getAnalysisRequestId(),
                "ANALYSIS_REQUESTED",
                evidence.getOriginalHashValue(),
                storagePathForAnalysis(evidence),
                "AI 분석 요청 생성 및 큐 등록 완료",
                jsonPayloadWriter.toJson(analysisRequestedPayload(evidence, savedRequest, caseName, message)),
                null
        );
    }

    public void recordDispatchError(
            Long userId,
            Evidence evidence,
            AnalysisRequest savedRequest,
            String errorCode,
            String message
    ) {
        custodyLogService.record(
                userId,
                CustodyTargetType.ANALYSIS_REQUEST,
                savedRequest.getAnalysisRequestId(),
                "ERROR_OCCURRED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                S3_COPY_NOT_READY.equals(errorCode)
                        ? "분석용 S3 사본 확인 실패"
                        : "분석 요청 큐 등록 실패",
                jsonPayloadWriter.toJson(dispatchErrorPayload(evidence, savedRequest, errorCode, message)),
                null
        );
    }

    public void recordQueuePublishError(Long userId, Evidence evidence, AnalysisRequest savedRequest) {
        custodyLogService.record(
                userId,
                CustodyTargetType.ANALYSIS_REQUEST,
                savedRequest.getAnalysisRequestId(),
                "ERROR_OCCURRED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                "분석 요청 큐 등록 실패",
                jsonPayloadWriter.toJson(queuePublishErrorPayload(evidence, savedRequest)),
                null
        );
    }

    private Map<String, Object> analysisRequestedPayload(
            Evidence evidence,
            AnalysisRequest savedRequest,
            String caseName,
            AnalysisJobMessage message
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("analysisRequestId", savedRequest.getAnalysisRequestId());
        payload.put("status", savedRequest.getStatus().name());
        payload.put("caseName", caseName);
        payload.put("fileType", "video");
        payload.put("filePath", message.getFilePath());
        payload.put("s3Bucket", message.getS3Bucket());
        payload.put("s3Region", message.getS3Region());
        payload.put("presignedDownloadUrl", message.getPresignedDownloadUrl());
        payload.put("queueRegistered", true);
        payload.put("queueName", RabbitMqConfig.ANALYSIS_QUEUE);
        payload.put("exchange", messagingProperties.getAnalysisExchange());
        payload.put("routingKey", messagingProperties.getVideoAnalysisRoutingKey());
        return payload;
    }

    private Map<String, Object> dispatchErrorPayload(
            Evidence evidence,
            AnalysisRequest savedRequest,
            String errorCode,
            String message
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", RABBITMQ_PUBLISH_STEP);
        payload.put("errorCode", errorCode);
        payload.put("message", message);
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("analysisRequestId", savedRequest.getAnalysisRequestId());
        payload.put("queueName", RabbitMqConfig.ANALYSIS_QUEUE);
        payload.put("exchange", messagingProperties.getAnalysisExchange());
        payload.put("routingKey", messagingProperties.getVideoAnalysisRoutingKey());
        return payload;
    }

    private Map<String, Object> queuePublishErrorPayload(Evidence evidence, AnalysisRequest savedRequest) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", RABBITMQ_PUBLISH_STEP);
        payload.put("errorCode", RABBITMQ_PUBLISH_FAILED);
        payload.put("message", RABBITMQ_PUBLISH_FAILURE_MESSAGE);
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("analysisRequestId", savedRequest.getAnalysisRequestId());
        payload.put("queueName", RabbitMqConfig.ANALYSIS_QUEUE);
        return payload;
    }

    private Map<String, Object> copyPayload(Evidence evidence, String copyStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("copyStatus", copyStatus);
        payload.put("copyStoragePath", evidence.getCopyStoragePath());
        payload.put("hashAlgorithm", evidence.getHashAlgorithm());
        return payload;
    }

    private Map<String, Object> analysisPayload(AnalysisRequest request, Evidence evidence, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("analysisRequestId", request.getAnalysisRequestId());
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("status", status);
        payload.put("fileType", "video");
        return payload;
    }

    private String subjectHash(Evidence evidence) {
        if (evidence.getCopyHashValue() != null && !evidence.getCopyHashValue().isBlank()) {
            return evidence.getCopyHashValue();
        }
        return evidence.getOriginalHashValue();
    }

    private String storagePathForAnalysis(Evidence evidence) {
        if (evidence.getCopyStoragePath() != null && !evidence.getCopyStoragePath().isBlank()) {
            return evidence.getCopyStoragePath();
        }
        return evidence.getOriginalStoragePath();
    }
}
