package com.example.demo.service.analysis;

import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.AnalysisStatusResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.util.AnalysisStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisStatusService {

    private final EvidenceAccessService evidenceAccessService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisQueueMetricsResolver queueMetricsResolver;

    public AnalysisStatusResponse getStatus(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireReadable(user, evidenceId);

        return analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                .map(this::toResponse)
                .orElseGet(() -> AnalysisStatusResponse.builder()
                        .evidenceId(evidence.getEvidenceId())
                        .analysisRequestId(0L)
                        .status("PENDING")
                        .queueStatus("WAITING")
                        .progressPercent(0)
                        .build());
    }

    private AnalysisStatusResponse toResponse(AnalysisRequest request) {
        AnalysisStatusResponse.AnalysisStatusResponseBuilder builder = AnalysisStatusResponse.builder()
                .evidenceId(request.getEvidenceId())
                .analysisRequestId(request.getAnalysisRequestId())
                .status(AnalysisStatusMapper.toApiStatus(request.getStatus()))
                .queueStatus(AnalysisStatusMapper.toQueueStatus(request.getStatus()))
                .progressPercent(request.getProgressPercent());

        if (request.getStatus() == AnalysisStatus.FAILED
                || (request.getStatus() == AnalysisStatus.COMPLETED
                && request.getErrorCode() != null
                && !request.getErrorCode().isBlank())) {
            builder.errorCode(request.getErrorCode())
                    .errorMessage(request.getErrorMessage());
        }
        AnalysisQueueMetricsResolver.QueueMetrics queueMetrics = queueMetricsResolver.resolve(request);
        if (queueMetrics != null) {
            builder.queueDepth(queueMetrics.queueDepth())
                    .queuePosition(queueMetrics.queuePosition());
        }
        return builder.build();
    }
}
