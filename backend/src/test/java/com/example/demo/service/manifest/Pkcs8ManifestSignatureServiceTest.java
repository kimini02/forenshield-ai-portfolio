package com.example.demo.service.manifest;

import com.example.demo.config.EvidenceManifestSigningProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class Pkcs8ManifestSignatureServiceTest {

    @Autowired
    private ManifestSignatureService manifestSignatureService;

    @Autowired
    private EvidenceManifestSigningProperties signingProperties;

    @Test
    @DisplayName("플랫폼 PKCS#8 키로 Manifest 서명·검증이 된다")
    void signAndVerifyManifest() {
        String manifestJson = "{\"caseId\":\"CASE-1\",\"evidenceId\":1}";

        String signature = manifestSignatureService.signManifest(manifestJson);

        assertThat(signature).isNotBlank();
        assertThat(manifestSignatureService.verifyManifest(manifestJson, signature)).isTrue();
        assertThat(manifestSignatureService.verifyManifest(manifestJson + " ", signature)).isFalse();
        assertThat(manifestSignatureService.getSignatureAlgorithm()).isEqualTo("SHA256withRSA");
        assertThat(manifestSignatureService.getSignerCertificateSubject()).contains("ForenShield Forensics CA");
        assertThat(signingProperties.getPrivateKeyLocation()).contains("platform-signing-key.pem");
    }
}
