package com.example.demo.service.blockchain.client;

import com.example.demo.domain.enums.BlockchainAnchorType;

/**
 * INF Fabric Anchor Gateway payload (hash + optional signature / off-chain refs).
 */
public record BlockchainAnchorRequest(
        String subjectHash,
        BlockchainAnchorType anchorType,
        String network,
        String clientId,
        Long evidenceId,
        Long reportId,
        String merkleBatchDate,
        Integer merkleLeafCount,
        String signature,
        String signerCertHash,
        Boolean certVerified,
        String offchainLogHash,
        OffchainRef offchainRef,
        AnalysisModelRef analysisModel,
        java.util.List<AnalysisModuleRef> analysisModules
) {
    public static BlockchainAnchorRequest of(
            String subjectHash,
            BlockchainAnchorType anchorType,
            String network,
            String clientId,
            Long evidenceId,
            Long reportId,
            String merkleBatchDate,
            Integer merkleLeafCount
    ) {
        return new BlockchainAnchorRequest(
                subjectHash,
                anchorType,
                network,
                clientId,
                evidenceId,
                reportId,
                merkleBatchDate,
                merkleLeafCount,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public record AnalysisModelRef(
            String name,
            String version,
            String identifier
    ) {
    }

    public record AnalysisModuleRef(
            String module,
            String name,
            String version
    ) {
    }
}
