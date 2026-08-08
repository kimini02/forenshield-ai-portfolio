package com.example.demo.service.custody;

import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.dto.detail.RecoveryScoreDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RQ-DTL-071 Recovery Score · RQ-DTL-072 데이터 소실도 산출.
 */
@Service
public class RecoveryScoreService {

    public RecoveryScoreDto calculate(EvidenceMetadata metadata) {
        if (metadata == null) {
            return build(0, 100, "CRITICAL", List.of("METADATA_NOT_FOUND"));
        }

        List<String> factors = new ArrayList<>();
        int score;
        ExtractionStatus status = metadata.getExtractionStatus();
        if (status == null || status == ExtractionStatus.FAILED) {
            factors.add("METADATA_EXTRACTION_FAILED");
            score = 30;
        } else if (status == ExtractionStatus.PARTIAL) {
            factors.add("METADATA_EXTRACTION_PARTIAL");
            score = 70;
        } else {
            score = 100;
        }

        if (metadata.getWidth() == null || metadata.getWidth() <= 0) {
            score -= 10;
            factors.add("WIDTH_MISSING");
        }
        if (metadata.getHeight() == null || metadata.getHeight() <= 0) {
            score -= 10;
            factors.add("HEIGHT_MISSING");
        }
        if (metadata.getDurationSec() == null || metadata.getDurationSec() <= 0) {
            score -= 15;
            factors.add("DURATION_MISSING");
        }
        if (metadata.getCodec() == null || metadata.getCodec().isBlank()) {
            score -= 10;
            factors.add("CODEC_MISSING");
        }
        if (metadata.getFps() == null || metadata.getFps() <= 0) {
            score -= 5;
            factors.add("FPS_MISSING");
        }
        if (metadata.getFfprobeJson() == null || metadata.getFfprobeJson().isBlank()) {
            score -= 10;
            factors.add("FFPROBE_JSON_MISSING");
        }
        if (metadata.getExtractionError() != null && !metadata.getExtractionError().isBlank()) {
            score -= 15;
            factors.add("EXTRACTION_ERROR_PRESENT");
        }
        boolean hasAudioIndicators = metadata.getSampleRate() != null || metadata.getChannels() != null;
        if (!hasAudioIndicators && metadata.getExtractionStatus() == ExtractionStatus.SUCCESS) {
            score -= 5;
            factors.add("AUDIO_STREAM_NOT_DETECTED");
        }

        score = Math.max(0, Math.min(100, score));
        int dataLoss = 100 - score;
        String grade = toGrade(score);

        if (factors.isEmpty()) {
            factors.add("METADATA_COMPLETE");
        }

        return build(score, dataLoss, grade, factors);
    }

    private RecoveryScoreDto build(int score, int dataLoss, String grade, List<String> factors) {
        return RecoveryScoreDto.builder()
                .recoveryScore(score)
                .dataLossPercent(dataLoss)
                .grade(grade)
                .factors(factors)
                .build();
    }

    private String toGrade(int score) {
        if (score >= 80) {
            return "HIGH";
        }
        if (score >= 50) {
            return "MEDIUM";
        }
        if (score >= 20) {
            return "LOW";
        }
        return "CRITICAL";
    }
}
