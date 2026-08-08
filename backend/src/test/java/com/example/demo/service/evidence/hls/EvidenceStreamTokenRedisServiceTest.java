package com.example.demo.service.evidence.hls;

import com.example.demo.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceStreamTokenRedisServiceTest {

    private EvidenceStreamTokenRedisService service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setStepUpExpirationMinutes(15);
        service = new EvidenceStreamTokenRedisService(null, jwtProperties);
    }

    @Test
    void issueAndResolve_returnsUserAndEvidenceId() {
        String token = service.issueToken(42L, 7L);

        var context = service.resolve(token);

        assertThat(context).isPresent();
        assertThat(context.get().userId()).isEqualTo(42L);
        assertThat(context.get().evidenceId()).isEqualTo(7L);
    }

    @Test
    void resolve_unknownToken_returnsEmpty() {
        assertThat(service.resolve("missing-token")).isEmpty();
    }

}
