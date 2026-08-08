package com.example.demo.service.blockchain.client;

public record BlockchainAnchorResult(
        String transactionHash,
        Long blockNumber,
        boolean success,
        String errorMessage
) {
}
