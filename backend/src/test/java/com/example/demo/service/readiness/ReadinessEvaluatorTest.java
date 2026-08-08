package com.example.demo.service.readiness;

import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.domain.enums.ReadinessTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessEvaluatorTest {

    private final ReadinessEvaluator evaluator = new ReadinessEvaluator();

    @Test
    void evaluateFromFfprobe_returnsGoodForHdVideo() {
        EvidenceMetadata metadata = baseMetadata();
        metadata.setWidth(1920);
        metadata.setHeight(1080);
        metadata.setDurationSec(30);
        metadata.setFps(30.0);

        var snapshot = evaluator.evaluateFromFfprobe(metadata);

        assertThat(snapshot.getReadinessTier()).isEqualTo(ReadinessTier.GOOD);
        assertThat(snapshot.getConfidenceCap()).isEqualTo(100);
        assertThat(snapshot.isRequiresAcknowledgement()).isFalse();
    }

    @Test
    void evaluateFromFfprobe_returnsPoorForLowResolution() {
        EvidenceMetadata metadata = baseMetadata();
        metadata.setWidth(320);
        metadata.setHeight(240);
        metadata.setDurationSec(30);
        metadata.setFps(30.0);

        var snapshot = evaluator.evaluateFromFfprobe(metadata);

        assertThat(snapshot.getReadinessTier()).isEqualTo(ReadinessTier.POOR);
        assertThat(snapshot.isRequiresAcknowledgement()).isTrue();
        assertThat(snapshot.getReasons()).anyMatch(reason -> reason.contains("해상도"));
    }

    @Test
    void evaluateFromFfprobe_returnsPoorWhenMetadataExtractionFailed() {
        EvidenceMetadata metadata = baseMetadata();
        metadata.setExtractionStatus(ExtractionStatus.FAILED);
        metadata.setExtractionError("ffprobe error");

        var snapshot = evaluator.evaluateFromFfprobe(metadata);

        assertThat(snapshot.getReadinessTier()).isEqualTo(ReadinessTier.POOR);
        assertThat(snapshot.getReasons()).anyMatch(reason -> reason.contains("FAILED"));
    }

    private EvidenceMetadata baseMetadata() {
        EvidenceMetadata metadata = new EvidenceMetadata();
        metadata.setEvidenceId(1L);
        metadata.setExtractionStatus(ExtractionStatus.SUCCESS);
        return metadata;
    }
}
