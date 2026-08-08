package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.worker")
public class AnalysisWorkerProperties {

    /** local | simulated | ai */
    private String mode = "local";

    private long stepDelayMs = 600;

    /** QUEUED/ANALYZING 상태가 이 시간(분)을 초과하면 실패 처리 */
    private long staleTimeoutMinutes = 120;

    private boolean staleReaperEnabled = true;

    private long staleReaperIntervalMs = 300_000;

    public boolean isAiMode() {
        return "ai".equalsIgnoreCase(mode);
    }
}
