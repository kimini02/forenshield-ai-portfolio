package com.example.demo.messaging;

import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.service.analysis.AnalysisJobEnqueuer;
import com.example.demo.service.analysis.AnalysisWorkerService;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnExpression("'${spring.rabbitmq.host:}'.length() == 0 || '${analysis.worker.mode:local}'.equalsIgnoreCase('local')")
public class LocalAnalysisJobEnqueuer implements AnalysisJobEnqueuer {

    private final AnalysisWorkerService analysisWorkerService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "local-analysis-queue");
        thread.setDaemon(true);
        return thread;
    });

    public LocalAnalysisJobEnqueuer(AnalysisWorkerService analysisWorkerService) {
        this.analysisWorkerService = analysisWorkerService;
    }

    @Override
    public void enqueue(AnalysisJobMessage message) {
        Runnable task = () -> analysisWorkerService.processJob(message.getAnalysisRequestId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.submit(task);
                }
            });
            return;
        }
        executor.submit(task);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
