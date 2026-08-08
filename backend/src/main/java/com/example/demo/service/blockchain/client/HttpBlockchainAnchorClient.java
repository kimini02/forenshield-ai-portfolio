package com.example.demo.service.blockchain.client;

import com.example.demo.config.BlockchainAnchorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls INF Hyperledger Fabric Anchor Gateway (REQ-052).
 * BE does not use Fabric SDK or wallet keys.
 */
@Component
@ConditionalOnProperty(name = "blockchain.anchor.mode", havingValue = "http")
public class HttpBlockchainAnchorClient implements BlockchainAnchorClient {

    private final BlockchainAnchorProperties properties;
    private final RestClient restClient;

    public HttpBlockchainAnchorClient(BlockchainAnchorProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getHttpConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getHttpReadTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public BlockchainAnchorResult anchor(BlockchainAnchorRequest request) {
        if (properties.getHttpUrl() == null || properties.getHttpUrl().isBlank()) {
            return new BlockchainAnchorResult(
                    null,
                    null,
                    false,
                    "blockchain.anchor.http-url is not configured"
            );
        }

        try {
            var spec = restClient.post()
                    .uri(properties.getHttpUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toGatewayBody(request));

            if (properties.getHttpApiKey() != null && !properties.getHttpApiKey().isBlank()) {
                spec = spec.header("X-Api-Key", properties.getHttpApiKey());
            }

            Map<?, ?> response = spec.retrieve().body(Map.class);

            if (response == null || response.get("transactionHash") == null) {
                return new BlockchainAnchorResult(
                        null,
                        null,
                        false,
                        "Blockchain gateway returned empty transactionHash"
                );
            }

            Long blockNumber = response.get("blockNumber") instanceof Number number
                    ? number.longValue()
                    : null;
            return new BlockchainAnchorResult(
                    String.valueOf(response.get("transactionHash")),
                    blockNumber,
                    true,
                    null
            );
        } catch (Exception ex) {
            return new BlockchainAnchorResult(null, null, false, ex.getMessage());
        }
    }

    private Map<String, Object> toGatewayBody(BlockchainAnchorRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subjectHash", request.subjectHash());
        body.put("anchorType", request.anchorType().name());
        body.put("network", request.network());
        body.put("clientId", request.clientId());
        if (request.evidenceId() != null) {
            body.put("evidenceId", request.evidenceId());
        }
        if (request.reportId() != null) {
            body.put("reportId", request.reportId());
        }
        if (request.merkleBatchDate() != null) {
            body.put("merkleBatchDate", request.merkleBatchDate());
        }
        if (request.merkleLeafCount() != null) {
            body.put("merkleLeafCount", request.merkleLeafCount());
        }
        if (request.signature() != null && !request.signature().isBlank()) {
            body.put("signature", request.signature());
        }
        if (request.signerCertHash() != null && !request.signerCertHash().isBlank()) {
            body.put("signerCertHash", request.signerCertHash());
        }
        if (request.certVerified() != null) {
            body.put("certVerified", request.certVerified());
        }
        if (request.offchainLogHash() != null && !request.offchainLogHash().isBlank()) {
            body.put("offchainLogHash", request.offchainLogHash());
        }
        if (request.offchainRef() != null && !request.offchainRef().isEmpty()) {
            body.put("offchainRef", request.offchainRef().toMap());
        }
        if (request.analysisModel() != null) {
            body.put("analysisModel", request.analysisModel());
        }
        if (request.analysisModules() != null && !request.analysisModules().isEmpty()) {
            body.put("analysisModules", request.analysisModules());
        }
        return body;
    }
}
