package com.example.demo.service.blockchain.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Off-chain location pointers only (no payload content on ledger).
 */
public record OffchainRef(
        String manifestStoragePath,
        String originalStoragePath,
        String reportStoragePath,
        String custodyLogBundleRef
) {
    public static OffchainRef ofEvidence(String manifestStoragePath, String originalStoragePath) {
        return new OffchainRef(blankToNull(manifestStoragePath), blankToNull(originalStoragePath), null, null);
    }

    public static OffchainRef ofReport(String reportStoragePath, String originalStoragePath) {
        return new OffchainRef(null, blankToNull(originalStoragePath), blankToNull(reportStoragePath), null);
    }

    public static OffchainRef ofMerkleBatch(String batchDate) {
        if (batchDate == null || batchDate.isBlank()) {
            return null;
        }
        return new OffchainRef(null, null, null, "rds:custody_logs?batchDate=" + batchDate);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        putIfPresent(map, "manifestStoragePath", manifestStoragePath);
        putIfPresent(map, "originalStoragePath", originalStoragePath);
        putIfPresent(map, "reportStoragePath", reportStoragePath);
        putIfPresent(map, "custodyLogBundleRef", custodyLogBundleRef);
        return map;
    }

    public boolean isEmpty() {
        return toMap().isEmpty();
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
