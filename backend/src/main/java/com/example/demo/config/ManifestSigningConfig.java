package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
public class ManifestSigningConfig {

    @Bean
    SecretsManagerClient secretsManagerClient(@Value("${aws.region:ap-northeast-2}") String awsRegion) {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
