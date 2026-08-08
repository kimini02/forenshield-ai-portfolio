package com.example.demo.service.custody;

import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.ExtractionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryScoreServiceTest {

    private final RecoveryScoreService recoveryScoreService = new RecoveryScoreService();

    @Test
    void calculate_returnsCriticalWhenMetadataMissing() {
        var result = recoveryScoreService.calculate(null);

        assertThat(result.getRecoveryScore()).isZero();
        assertThat(result.getDataLossPercent()).isEqualTo(100);
        assertThat(result.getGrade()).isEqualTo("CRITICAL");
    }

    @Test
    void calculate_returnsHighWhenMetadataComplete() {
        EvidenceMetadata metadata = metadata(ExtractionStatus.SUCCESS);
        metadata.setWidth(1920);
        metadata.setHeight(1080);
        metadata.setDurationSec(120);
        metadata.setCodec("h264");
        metadata.setFps(30.0);
        metadata.setFfprobeJson("{}");
        metadata.setSampleRate(48000);
        metadata.setChannels(2);

        var result = recoveryScoreService.calculate(metadata);

        assertThat(result.getRecoveryScore()).isGreaterThanOrEqualTo(80);
        assertThat(result.getGrade()).isEqualTo("HIGH");
        assertThat(result.getDataLossPercent()).isLessThanOrEqualTo(20);
    }

    @Test
    void calculate_penalizesFailedExtraction() {
        EvidenceMetadata metadata = metadata(ExtractionStatus.FAILED);
        metadata.setExtractionError("ffprobe failed");

        var result = recoveryScoreService.calculate(metadata);

        assertThat(result.getRecoveryScore()).isLessThan(50);
        assertThat(result.getFactors()).contains("METADATA_EXTRACTION_FAILED");
    }

    private EvidenceMetadata metadata(ExtractionStatus status) {
        EvidenceMetadata metadata = new EvidenceMetadata();
        metadata.setEvidenceId(1L);
        metadata.setExtractionStatus(status);
        return metadata;
    }
}
