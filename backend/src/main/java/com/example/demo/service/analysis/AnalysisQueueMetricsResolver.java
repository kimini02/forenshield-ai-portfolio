package com.example.demo.service.analysis;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AnalysisQueueMetricsResolver {

    private final AnalysisRequestRepository analysisRequestRepository;

    QueueMetrics resolve(AnalysisRequest request) {
        if (request.getStatus() != AnalysisStatus.QUEUED) {
            return null;
        }
        long queueDepth = analysisRequestRepository.countByStatus(AnalysisStatus.QUEUED);
        long ahead = analysisRequestRepository.countByStatusAndRequestedAtBefore(
                AnalysisStatus.QUEUED,
                request.getRequestedAt()
        );
        return new QueueMetrics((int) queueDepth, (int) ahead + 1);
    }

    record QueueMetrics(int queueDepth, int queuePosition) {
    }
}
