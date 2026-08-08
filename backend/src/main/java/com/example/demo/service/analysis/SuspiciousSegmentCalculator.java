package com.example.demo.service.analysis;

import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** SK-675: 프레임별 위험 점수로 의심 구간(startTime/endTime) 산출 */
@Component
public class SuspiciousSegmentCalculator {

    public List<SuspiciousSegmentDto> compute(
            List<FrameRiskDto> frameRisks,
            double highRiskThreshold,
            double minSegmentSec
    ) {
        if (frameRisks == null || frameRisks.isEmpty()) {
            return List.of();
        }

        List<FrameRiskDto> sorted = frameRisks.stream()
                .sorted(Comparator.comparingDouble(FrameRiskDto::getTimestampSec))
                .toList();

        List<SuspiciousSegmentDto> segments = new ArrayList<>();
        Double segmentStart = null;
        Double segmentEnd = null;
        double segmentMax = 0.0;

        for (FrameRiskDto frame : sorted) {
            if (frame.getRiskScore() >= highRiskThreshold) {
                if (segmentStart == null) {
                    segmentStart = frame.getTimestampSec();
                    segmentEnd = frame.getTimestampSec();
                    segmentMax = frame.getRiskScore();
                } else {
                    segmentEnd = frame.getTimestampSec();
                    segmentMax = Math.max(segmentMax, frame.getRiskScore());
                }
                continue;
            }

            if (segmentStart != null) {
                segments.add(toSegment(segmentStart, segmentEnd, segmentMax));
                segmentStart = null;
                segmentEnd = null;
                segmentMax = 0.0;
            }
        }

        if (segmentStart != null) {
            segments.add(toSegment(segmentStart, segmentEnd, segmentMax));
        }

        return segments.stream()
                .filter(segment -> (segment.getEndTime() - segment.getStartTime()) >= minSegmentSec
                        || segment.getMaxRiskScore() >= highRiskThreshold)
                .toList();
    }

    private SuspiciousSegmentDto toSegment(double start, double end, double maxScore) {
        return SuspiciousSegmentDto.builder()
                .startTime(round3(start))
                .endTime(round3(end))
                .maxRiskScore(round3(maxScore))
                .reason("Frame risk score exceeded threshold")
                .build();
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
