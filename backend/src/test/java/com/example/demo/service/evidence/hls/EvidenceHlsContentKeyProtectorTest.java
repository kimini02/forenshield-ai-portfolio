package com.example.demo.service.evidence.hls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.config.HlsPackagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvidenceHlsContentKeyProtectorTest {

    private EvidenceHlsContentKeyProtector protector;

    @BeforeEach
    void setUp() {
        HlsPackagingProperties properties = new HlsPackagingProperties();
        properties.setContentKeyEncryptionSecret("test-hls-master-secret-32-chars-min");
        protector = new EvidenceHlsContentKeyProtector(properties);
    }

    @Test
    void encryptDecrypt_roundTripsContentKey() {
        byte[] plainKey = new byte[16];
        for (int i = 0; i < plainKey.length; i++) {
            plainKey[i] = (byte) i;
        }

        byte[] encrypted = protector.encrypt(plainKey);
        byte[] decrypted = protector.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(plainKey);
        assertThat(decrypted).isEqualTo(plainKey);
    }

    @Test
    void constructor_rejectsShortSecret() {
        HlsPackagingProperties properties = new HlsPackagingProperties();
        properties.setContentKeyEncryptionSecret("short");

        assertThatThrownBy(() -> new EvidenceHlsContentKeyProtector(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 16 characters");
    }
}
