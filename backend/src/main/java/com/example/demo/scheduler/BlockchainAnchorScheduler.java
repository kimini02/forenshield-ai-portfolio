package com.example.demo.scheduler;

import com.example.demo.config.BlockchainAnchorProperties;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blockchain.anchor.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class BlockchainAnchorScheduler {

    private final BlockchainAnchorProperties properties;
    private final BlockchainAnchorService blockchainAnchorService;

    @Scheduled(cron = "${blockchain.anchor.daily-cron:0 0 1 * * *}")
    public void anchorDailyMerkleRoot() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("Starting daily merkle root blockchain anchor job");
        blockchainAnchorService.anchorDailyMerkleRoot(LocalDate.now().minusDays(1));
    }
}
