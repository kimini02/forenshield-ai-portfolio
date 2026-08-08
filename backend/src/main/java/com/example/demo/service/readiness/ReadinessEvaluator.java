package com.example.demo.service.readiness;

import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.domain.enums.ReadinessSource;
import com.example.demo.domain.enums.ReadinessTier;
import com.example.demo.dto.readiness.ReadinessSnapshot;
import com.example.demo.dto.readiness.ReadinessVideoMetadataDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ffprobe 기반 메타데이터만으로 즉시 readiness 등급을 산출한다.
 * video_readiness.py 의 메타 게이트와 동일한 기본 임계값을 사용한다.
 * ffprobe 메타만으로 GOOD/CAUTION/POOR 판정
 */
@Component
public class ReadinessEvaluator {

    private static final String THRESHOLDS_VERSION = "notebook-ui-v1";
    private static final double MIN_DURATION_SEC = 3.0;
    private static final double MIN_FPS = 15.0;
    private static final int CAUTION_MIN_PIXELS = 640 * 480;
    private static final int POOR_MIN_PIXELS = 426 * 240;
    private static final int CONFIDENCE_CAP_POOR = 60;
    private static final int CONFIDENCE_CAP_CAUTION = 75;

    public ReadinessSnapshot evaluateFromFfprobe(EvidenceMetadata metadata) {
        if (metadata == null) {
            return blockSnapshot("메타데이터가 없습니다.");
        }

        if (metadata.getExtractionStatus() == ExtractionStatus.FAILED) {
            return poorSnapshot(
                    metadata,
                    List.of("메타데이터 추출에 실패했습니다. (extractionStatus=FAILED)")
            );
        }

        List<String> reasons = new ArrayList<>();
        ReadinessTier tier = ReadinessTier.GOOD;

        Integer width = metadata.getWidth();
        Integer height = metadata.getHeight();
        Integer durationSec = metadata.getDurationSec();
        Double fps = metadata.getFps();

        if (width != null && height != null) {
            int pixels = width * height;
            if (pixels < POOR_MIN_PIXELS) {
                tier = ReadinessTier.POOR;
                reasons.add("해상도 %dx%d (권장 720p 이상, 최소 480p)".formatted(width, height));
            } else if (pixels < CAUTION_MIN_PIXELS) {
                tier = maxTier(tier, ReadinessTier.CAUTION);
                reasons.add("해상도 %dx%d (권장 1280x720 이상)".formatted(width, height));
            }
        } else if (metadata.getExtractionStatus() == ExtractionStatus.PARTIAL) {
            tier = maxTier(tier, ReadinessTier.CAUTION);
            reasons.add("해상도 정보가 없습니다. (extractionStatus=PARTIAL)");
        }

        if (durationSec != null && durationSec < MIN_DURATION_SEC) {
            tier = ReadinessTier.POOR;
            reasons.add("재생 시간 %.1f초 (권장 %.0f초 이상)".formatted(durationSec.doubleValue(), MIN_DURATION_SEC));
        } else if (durationSec == null && metadata.getExtractionStatus() == ExtractionStatus.PARTIAL) {
            tier = maxTier(tier, ReadinessTier.CAUTION);
            reasons.add("재생 시간 정보가 없습니다.");
        }

        if (fps != null && fps > 0 && fps < MIN_FPS) {
            tier = maxTier(tier, ReadinessTier.CAUTION);
            reasons.add("FPS %.1f (권장 %.0f 이상)".formatted(fps, MIN_FPS));
        }

        return buildSnapshot(metadata, tier, reasons, ReadinessSource.FFPROBE);
    }

    private ReadinessSnapshot blockSnapshot(String reason) {
        return ReadinessSnapshot.builder()
                .source(ReadinessSource.FFPROBE)
                .checkedAt(LocalDateTime.now())
                .readinessTier(ReadinessTier.BLOCK)
                .confidenceCap(CONFIDENCE_CAP_POOR)
                .reasons(List.of(reason))
                .requiresAcknowledgement(false)
                .thresholdsVersion(THRESHOLDS_VERSION)
                .build();
    }

    private ReadinessSnapshot poorSnapshot(EvidenceMetadata metadata, List<String> reasons) {
        return buildSnapshot(metadata, ReadinessTier.POOR, reasons, ReadinessSource.FFPROBE);
    }

    private ReadinessSnapshot buildSnapshot(
            EvidenceMetadata metadata,
            ReadinessTier tier,
            List<String> reasons,
            ReadinessSource source
    ) {
        int confidenceCap = switch (tier) {
            case POOR, BLOCK -> CONFIDENCE_CAP_POOR;
            case CAUTION -> CONFIDENCE_CAP_CAUTION;
            case GOOD -> 100;
        };
        boolean requiresAck = tier == ReadinessTier.POOR || tier == ReadinessTier.CAUTION;

        return ReadinessSnapshot.builder()
                .source(source)
                .checkedAt(LocalDateTime.now())
                .readinessTier(tier)
                .confidenceCap(confidenceCap)
                .reasons(List.copyOf(reasons))
                .requiresAcknowledgement(requiresAck)
                .thresholdsVersion(THRESHOLDS_VERSION)
                .videoMetadata(toVideoMetadata(metadata))
                .build();
    }

    private ReadinessVideoMetadataDto toVideoMetadata(EvidenceMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return ReadinessVideoMetadataDto.builder()
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .fps(metadata.getFps())
                .durationSec(metadata.getDurationSec())
                .build();
    }

    private ReadinessTier maxTier(ReadinessTier current, ReadinessTier candidate) {
        return current.ordinal() >= candidate.ordinal() ? current : candidate;
    }
}
