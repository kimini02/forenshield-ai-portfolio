package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hls.packaging")
public class HlsPackagingProperties {

    private boolean enabled = true;

    /** 동시 FFmpeg 패키징 상한 */
    private int maxConcurrent = 2;

    /** 백필·스케줄러 1회 dequeue 건수 */
    private int batchSize = 10;

    /** 1건 패키징 타임아웃 (분) */
    private int timeoutMinutes = 30;

    /** HLS 세그먼트 길이 (초) */
    private int segmentDurationSec = 6;

    /** PACKAGING 고착 판단 (분) */
    private int stalePackagingMinutes = 45;

    private long backfillIntervalMs = 60_000;

    private long staleReaperIntervalMs = 300_000;

    /** READY hls/ prefix당 S3 stray 정리 배치 (1회 스케줄 틱) */
    private int artifactCleanupBatchSize = 20;

    /** content_key_enc 암호화용 마스터 시크릿 (운영 env 필수) */
    private String contentKeyEncryptionSecret = "forenshield-hls-dev-key-change-in-prod-32b";
}
