package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Profile("!test")
public class S3Config {

    @Value("${aws.region}")  // application.yaml 의 aws.region 값(ap-northeast-2)을 이 변수에 자동 주입
    private String region;

    @Bean
    public S3Client s3Client() {
        // 자격증명 명시 안 함 — Default Credentials Chain 사용
        // 로컬: AWS_PROFILE 환경변수 → EKS: IRSA(ServiceAccount) 자동 인식
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
