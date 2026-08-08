package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "evidence.manifest.signing")
public class EvidenceManifestSigningProperties {

    /** PEM 문자열 직접 주입 (K8s Secret 등). 설정 시 location보다 우선 */
    private String privateKeyPem = "";

    /** PEM 파일 위치 — classpath:, file: 지원 */
    private String privateKeyLocation = "classpath:crypto/platform-signing-key.pem";

    /** X.509 인증서 PEM (공개키·Subject 추출용) */
    private String certificatePem = "";

    private String certificateLocation = "classpath:crypto/platform-signing-cert.pem";

    /** AWS Secrets Manager Secret ID — JSON { privateKeyPem, certificatePem } */
    private String secretsManagerSecretId = "";
}
