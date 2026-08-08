package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "evidence.manifest")
public class EvidenceManifestProperties {

    /** Manifest 발급 기관 표시명 (단일 플랫폼 CA) */
    private String issuer = "ForenShield Digital Forensics";
}
