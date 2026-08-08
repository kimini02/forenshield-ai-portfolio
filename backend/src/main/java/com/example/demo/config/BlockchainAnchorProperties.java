package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blockchain.anchor")
public class BlockchainAnchorProperties {

    private boolean enabled = true;

    /** simulated (local) | http (INF Fabric/Polygon gateway) */
    private String mode = "simulated";

    private String network = "local-simulated";

    /** POST target — INF Fabric Anchor Gateway, e.g. https://host/api/v1/anchor */
    private String httpUrl = "";

    private String httpApiKey = "";

    private String clientId = "forenshield-be";

    private int httpConnectTimeoutMs = 5_000;

    private int httpReadTimeoutMs = 30_000;

    private String dailyCron = "0 0 1 * * *";

    private boolean schedulerEnabled = true;

    /** RQ-DTL-080: `{txHash}` placeholder — Fabric Explorer or block explorer URL */
    private String explorerUrlTemplate = "";

    public boolean isHttpMode() {
        return "http".equalsIgnoreCase(mode);
    }
}
