package com.example.demo.service.evidence.hls;

import com.example.demo.config.HlsPackagingProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@ConditionalOnProperty(name = "hls.packaging.enabled", havingValue = "true", matchIfMissing = true)
public class LocalHlsPackagingEnqueuer implements HlsPackagingEnqueuer {

    private final EvidenceHlsPackagingService packagingService;
    private final HlsPackagingProperties properties;
    private final ExecutorService executor;
    private final Semaphore concurrencyLimit;

    public LocalHlsPackagingEnqueuer(
            EvidenceHlsPackagingService packagingService,
            HlsPackagingProperties properties
    ) {
        this.packagingService = packagingService;
        this.properties = properties;
        this.concurrencyLimit = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, properties.getMaxConcurrent()),
                runnable -> {
                    Thread thread = new Thread(runnable, "hls-packaging-worker");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    @Override
    public void enqueue(Long evidenceId) {
        Runnable task = () -> {
            boolean acquired = false;
            try {
                concurrencyLimit.acquire();
                acquired = true;
                packagingService.packageEvidence(evidenceId);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("HLS packaging interrupted evidenceId={}", evidenceId);
            } finally {
                if (acquired) {
                    concurrencyLimit.release();
                }
            }
        };

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
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
