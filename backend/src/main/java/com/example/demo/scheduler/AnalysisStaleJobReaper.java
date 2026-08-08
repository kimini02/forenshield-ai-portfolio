package com.example.demo.scheduler;

import com.example.demo.config.AnalysisWorkerProperties;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.service.analysis.AnalysisWorkerService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "analysis.worker.stale-reaper-enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisStaleJobReaper {

    private final AnalysisWorkerProperties workerProperties;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisWorkerService analysisWorkerService;

    @Scheduled(fixedDelayString = "${analysis.worker.stale-reaper-interval-ms:300000}")
    public void reapStaleJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(workerProperties.getStaleTimeoutMinutes());
        List<AnalysisRequest> staleJobs = new ArrayList<>();
        staleJobs.addAll(analysisRequestRepository.findByStatusAndStartedAtBefore(AnalysisStatus.ANALYZING, cutoff));
        staleJobs.addAll(analysisRequestRepository.findByStatusAndRequestedAtBefore(AnalysisStatus.QUEUED, cutoff));

        for (AnalysisRequest request : staleJobs) {
            log.warn("Reaping stale analysis job analysisRequestId={} status={}",
                    request.getAnalysisRequestId(), request.getStatus());
            analysisWorkerService.failStaleAnalysisJob(request.getAnalysisRequestId());
        }
    }
}
