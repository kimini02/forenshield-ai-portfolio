package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.messaging")
public class AnalysisMessagingProperties {

    private String analysisExchange = "ai.analysis.exchange";
    private String resultExchange = "ai.result.exchange";
    private String deadLetterExchange = "ai.dead.exchange";
    private String videoAnalysisRoutingKey = "analyze.video";
    private String videoResultRoutingKey = "result.video";
    private String overlayRoutingKey = "overlay.video";
    private String overlayResultRoutingKey = "result.overlay";
    private int presignDurationMinutes = 120;
}
