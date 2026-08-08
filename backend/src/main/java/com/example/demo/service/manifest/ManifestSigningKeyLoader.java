package com.example.demo.service.manifest;

import com.example.demo.config.EvidenceManifestSigningProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Slf4j
@Component
public class ManifestSigningKeyLoader {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final SecretsManagerClient secretsManagerClient;

    public ManifestSigningKeyLoader(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            SecretsManagerClient secretsManagerClient
    ) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.secretsManagerClient = secretsManagerClient;
    }

    public ManifestSigningKeyMaterial load(EvidenceManifestSigningProperties properties) {
        String privateKeyPem;
        String certificatePem;

        if (StringUtils.hasText(properties.getSecretsManagerSecretId())) {
            SecretBundle bundle = loadFromSecretsManager(properties.getSecretsManagerSecretId());
            privateKeyPem = bundle.privateKeyPem();
            certificatePem = bundle.certificatePem();
            log.info("Loaded manifest signing key from Secrets Manager secretId={}",
                    properties.getSecretsManagerSecretId());
        } else {
            try {
                privateKeyPem = resolvePem(properties.getPrivateKeyPem(), properties.getPrivateKeyLocation(), "private key");
                certificatePem = resolvePem(properties.getCertificatePem(), properties.getCertificateLocation(), "certificate");
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read manifest signing key files", ex);
            }
        }

        try {
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            X509Certificate certificate = parseCertificate(certificatePem);
            return new ManifestSigningKeyMaterial(privateKey, certificate);
        } catch (Exception ex) {
            throw new IllegalStateException("Platform manifest signing key material could not be loaded", ex);
        }
    }

    private SecretBundle loadFromSecretsManager(String secretId) {
        try {
            String secretString = secretsManagerClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build()
            ).secretString();

            JsonNode root = objectMapper.readTree(secretString);
            String privateKeyPem = textField(root, "privateKeyPem", "private_key_pem", "privateKey");
            String certificatePem = textField(root, "certificatePem", "certificate_pem", "certificate");
            if (!StringUtils.hasText(privateKeyPem) || !StringUtils.hasText(certificatePem)) {
                throw new IllegalStateException(
                        "Secrets Manager secret must contain privateKeyPem and certificatePem JSON fields");
            }
            return new SecretBundle(privateKeyPem, certificatePem);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load manifest signing secret id=" + secretId, ex);
        }
    }

    private String textField(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }

    private String resolvePem(String inlinePem, String location, String label) throws IOException {
        if (StringUtils.hasText(inlinePem)) {
            return inlinePem;
        }
        if (!StringUtils.hasText(location)) {
            throw new IllegalStateException("Manifest signing " + label + " is not configured");
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Manifest signing " + label + " resource not found: " + location);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    PrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = decodePemBlock(pem.trim(), "PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    X509Certificate parseCertificate(String pem) throws Exception {
        byte[] der = decodePemBlock(pem.trim(), "CERTIFICATE");
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    private byte[] decodePemBlock(String pem, String... labels) {
        for (String label : labels) {
            String begin = "-----BEGIN " + label + "-----";
            String end = "-----END " + label + "-----";
            int start = pem.indexOf(begin);
            int finish = pem.indexOf(end);
            if (start >= 0 && finish > start) {
                String base64 = pem.substring(start + begin.length(), finish)
                        .replaceAll("\\s", "");
                return Base64.getDecoder().decode(base64);
            }
        }
        throw new IllegalArgumentException("Unsupported PEM format");
    }

    private record SecretBundle(String privateKeyPem, String certificatePem) {
    }
}
