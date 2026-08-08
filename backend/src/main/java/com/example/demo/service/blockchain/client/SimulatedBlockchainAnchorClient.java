package com.example.demo.service.blockchain.client;

import com.example.demo.service.evidence.HashService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blockchain.anchor.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedBlockchainAnchorClient implements BlockchainAnchorClient {

    private final HashService hashService;

    @Override
    public BlockchainAnchorResult anchor(BlockchainAnchorRequest request) {
        String payload = "simulated-anchor|"
                + request.anchorType().name()
                + "|"
                + request.subjectHash();
        String transactionHash = "0x" + hashService.generateSha256(payload.getBytes());
        return new BlockchainAnchorResult(transactionHash, 1L, true, null);
    }
}
