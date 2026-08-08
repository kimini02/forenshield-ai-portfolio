package com.example.demo.service.evidence.hls;

import com.example.demo.config.HlsPackagingProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HLS AES-128 content key를 DB 저장 전 암호화/복호화 (Phase 3 key API용 decrypt 포함).
 */
@Component
public class EvidenceHlsContentKeyProtector {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EvidenceHlsContentKeyProtector(HlsPackagingProperties properties) {
        this.masterKey = deriveKey(properties.getContentKeyEncryptionSecret());
    }

    public byte[] encrypt(byte[] plainKey) {
        if (plainKey == null || plainKey.length == 0) {
            throw new IllegalArgumentException("plainKey is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainKey);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return buffer.array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt HLS content key", ex);
        }
    }

    public byte[] decrypt(byte[] encrypted) {
        if (encrypted == null || encrypted.length <= IV_BYTES) {
            throw new IllegalArgumentException("encrypted key is invalid");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt HLS content key", ex);
        }
    }

    private static SecretKeySpec deriveKey(String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("hls.packaging.content-key-encryption-secret must be at least 16 characters");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to derive HLS master key", ex);
        }
    }
}
