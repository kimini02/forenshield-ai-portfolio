package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "report.issue.worker")
public class ReportIssueWorkerProperties {

    private boolean enabled = true;
    private int batchSize = 10;
    private long pollIntervalMs = 5_000;
    private long processingTimeoutMs = 300_000;
    private int maxAttempts = 3;
    private long retryDelayMs = 30_000;
}
