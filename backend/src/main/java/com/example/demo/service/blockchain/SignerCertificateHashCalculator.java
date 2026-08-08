package com.example.demo.service.blockchain;

import com.example.demo.service.evidence.HashService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * signerCertHash = SHA-256(UTF-8(normalizePem(certificatePem))).
 */
@Component
@RequiredArgsConstructor
public class SignerCertificateHashCalculator {

    private final HashService hashService;

    public String hashPem(String certificatePem) {
        if (certificatePem == null || certificatePem.isBlank()) {
            return null;
        }
        String normalized = normalizePem(certificatePem);
        return hashService.generateSha256(normalized.getBytes(StandardCharsets.UTF_8));
    }

    static String normalizePem(String pem) {
        return pem.trim().replace("\r\n", "\n").replace("\r", "\n");
    }
}
