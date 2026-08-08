package com.example.demo.service.analysis;

import com.example.demo.service.custody.AnalysisCustodyLogService;
import com.example.demo.service.dashboard.DashboardStatsCache;
import com.example.demo.service.evidence.EvidenceCopyService;
import com.example.demo.config.AnalysisWorkerProperties;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.dto.AnalysisStartResultItem;
import com.example.demo.dto.StartAnalysisRequest;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.exception.AnalysisCopyException;
import com.example.demo.exception.AnalysisDispatchException;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.service.readiness.EvidenceReadinessService;
import com.example.demo.dto.readiness.ReadinessSnapshot;
import com.example.demo.util.AnalysisStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String RABBITMQ_PUBLISH_FAILED = AnalysisCustodyLogService.RABBITMQ_PUBLISH_FAILED;
    private static final String RABBITMQ_PUBLISH_FAILURE_MESSAGE = AnalysisCustodyLogService.RABBITMQ_PUBLISH_FAILURE_MESSAGE;

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisJobEnqueuer analysisJobEnqueuer;
    private final AnalysisJobMessageFactory analysisJobMessageFactory;
    private final EvidenceCopyService evidenceCopyService;
    private final AnalysisCustodyLogService analysisCustodyLogService;
    private final AnalysisWorkerProperties workerProperties;
    private final AnalysisWorkerService analysisWorkerService;
    private final DashboardStatsCache dashboardStatsCache;
    private final EvidenceReadinessService evidenceReadinessService;

    @Transactional
    public StartAnalysisResponse startAnalysis(User user, StartAnalysisRequest request) {
        List<Long> evidenceIds = resolveEvidenceIds(request);
        String trimmedCaseName = resolveCaseName(request.getCaseName(), evidenceIds, user.getUserId());

        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, user.getUserId());

        if (evidences.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "분석할 수 있는 증거를 찾을 수 없습니다.");
        }

        for (Evidence evidence : evidences) {
            evidenceReadinessService.assertQualityAcknowledged(
                    evidence.getEvidenceId(), request.getAcknowledgeQualityWarning());
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> startedEvidenceIds = new ArrayList<>();
        List<AnalysisStartResultItem> results = new ArrayList<>();

        for (Evidence evidence : evidences) {
            if (analysisRequestRepository.existsByEvidenceIdAndStatus(
                    evidence.getEvidenceId(), AnalysisStatus.COMPLETED)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "ANALYSIS_ALREADY_COMPLETED",
                        "이미 분석이 완료된 증거입니다.");
            }

            if (analysisRequestRepository.existsByEvidenceIdAndStatusIn(
                    evidence.getEvidenceId(), List.of(AnalysisStatus.QUEUED, AnalysisStatus.ANALYZING))) {
                AnalysisRequest existing = analysisRequestRepository
                        .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                        .orElseThrow();
                results.add(toStartResult(existing, evidence.getEvidenceId(), true, null, null));
                continue;
            }

            ReadinessSnapshot readinessSnapshot =
                    evidenceReadinessService.resolveStoredSnapshot(evidence.getEvidenceId());
            if (readinessSnapshot != null
                    && readinessSnapshot.isRequiresAcknowledgement()
                    && Boolean.TRUE.equals(request.getAcknowledgeQualityWarning())) {
                analysisCustodyLogService.recordQualityWarningAcknowledged(
                        user.getUserId(), evidence, readinessSnapshot);
            }

            evidence.updateCaseInfo(trimmedCaseName);
            try {
                evidenceCopyService.prepareCopyForAnalysis(evidence, user.getUserId());
            } catch (AnalysisCopyException ex) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ex.getErrorCode(),
                        ex.getMessage()
                );
            }
            evidenceRepository.save(evidence);

            AnalysisRequest analysisRequest = new AnalysisRequest();
            analysisRequest.setEvidenceId(evidence.getEvidenceId());
            analysisRequest.setRequestedBy(user.getUserId());
            analysisRequest.setStatus(AnalysisStatus.QUEUED);
            analysisRequest.setProgressPercent(0);
            analysisRequest.setRequestedAt(now);
            AnalysisRequest savedRequest = analysisRequestRepository.save(analysisRequest);

            try {
                AnalysisJobMessage message = analysisJobMessageFactory.buildForGpuDispatch(
                        evidence, savedRequest, trimmedCaseName);
                analysisJobEnqueuer.enqueue(message);
                analysisCustodyLogService.recordAnalysisRequested(
                        user.getUserId(), evidence, savedRequest, trimmedCaseName, message);
                if (workerProperties.isAiMode()) {
                    analysisWorkerService.markDispatchedToAi(savedRequest.getAnalysisRequestId());
                }
                startedEvidenceIds.add(evidence.getEvidenceId());
                results.add(toStartResult(savedRequest, evidence.getEvidenceId(), true, null, null));
            } catch (AnalysisDispatchException ex) {
                savedRequest.setStatus(AnalysisStatus.FAILED);
                savedRequest.setErrorCode(ex.getErrorCode());
                savedRequest.setErrorMessage(ex.getMessage());
                analysisRequestRepository.save(savedRequest);
                analysisCustodyLogService.recordDispatchError(
                        user.getUserId(), evidence, savedRequest, ex.getErrorCode(), ex.getMessage());
                results.add(toStartResult(savedRequest, evidence.getEvidenceId(), false, ex.getErrorCode(), ex.getMessage()));
            } catch (Exception ex) {
                savedRequest.setStatus(AnalysisStatus.FAILED);
                savedRequest.setErrorCode(RABBITMQ_PUBLISH_FAILED);
                savedRequest.setErrorMessage(RABBITMQ_PUBLISH_FAILURE_MESSAGE);
                analysisRequestRepository.save(savedRequest);
                analysisCustodyLogService.recordQueuePublishError(user.getUserId(), evidence, savedRequest);
                results.add(toStartResult(
                        savedRequest,
                        evidence.getEvidenceId(),
                        false,
                        RABBITMQ_PUBLISH_FAILED,
                        RABBITMQ_PUBLISH_FAILURE_MESSAGE
                ));
            }
        }

        dashboardStatsCache.invalidate(user.getUserId());
        return StartAnalysisResponse.builder()
                .success(true)
                .message("분석 요청이 등록되었습니다.")
                .caseName(trimmedCaseName)
                .startedCount(startedEvidenceIds.size())
                .evidenceIds(startedEvidenceIds)
                .results(results)
                .build();
    }

    private AnalysisStartResultItem toStartResult(
            AnalysisRequest request,
            Long evidenceId,
            boolean queueRegistered,
            String errorCode,
            String errorMessage
    ) {
        return AnalysisStartResultItem.builder()
                .evidenceId(evidenceId)
                .analysisRequestId(request.getAnalysisRequestId())
                .queueRegistered(queueRegistered)
                .status(AnalysisStatusMapper.toApiStatus(request.getStatus()))
                .queueStatus(AnalysisStatusMapper.toQueueStatus(request.getStatus()))
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private List<Long> resolveEvidenceIds(StartAnalysisRequest request) {
        if (request.getEvidenceId() != null) {
            return List.of(request.getEvidenceId());
        }
        if (request.getEvidenceIds() != null && !request.getEvidenceIds().isEmpty()) {
            return request.getEvidenceIds();
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "분석할 증거를 선택해 주세요.");
    }

    private String resolveCaseName(String caseName, List<Long> evidenceIds, Long uploaderId) {
        if (caseName != null && !caseName.isBlank()) {
            return caseName.trim();
        }

        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, uploaderId);
        return evidences.stream()
                .map(Evidence::getCaseName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건명은 필수입니다."));
    }
}
