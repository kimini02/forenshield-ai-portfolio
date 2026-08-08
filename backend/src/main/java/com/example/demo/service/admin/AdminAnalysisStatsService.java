package com.example.demo.service.admin;

import com.example.demo.dto.admin.AdminAnalysisStatsResponse;
import com.example.demo.dto.admin.AdminRiskDistribution;
import com.example.demo.dto.admin.AdminWeeklyAnalysisPoint;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RQ-ADMIN-150: 관리자 분석 통계 (전체 사용자·미삭제 증거 집계).
 * 집계 기준: docs/database/erd.md §8.2 · §8.6
 */
@Service
@RequiredArgsConstructor
public class AdminAnalysisStatsService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;

    @Transactional(readOnly = true)
    public AdminAnalysisStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDateTime weekStartAt = weekStart.atStartOfDay();
        LocalDateTime weekEndExclusive = weekEnd.plusDays(1).atStartOfDay();

        long weeklyTotalCount = analysisRequestRepository.countCompletedBetween(weekStartAt, weekEndExclusive);
        long weeklyCompletedCount = weeklyTotalCount;
        long weeklyDetectedCount = analysisRequestRepository.countDeepfakeDetectedBetween(
                weekStartAt,
                weekEndExclusive
        );

        double deepfakeDetectionRate = weeklyCompletedCount == 0
                ? 0.0
                : roundOneDecimal((weeklyDetectedCount * 100.0) / weeklyCompletedCount);

        List<AnalysisRequest> weeklyCompletedRequests =
                analysisRequestRepository.findCompletedRequestsBetween(weekStartAt, weekEndExclusive);
        double averageAnalysisMinutes = calculateAverageAnalysisMinutes(weeklyCompletedRequests);

        List<AdminWeeklyAnalysisPoint> weeklyPoints = new ArrayList<>(7);
        for (int offset = 0; offset < 7; offset += 1) {
            LocalDate date = weekStart.plusDays(offset);
            LocalDateTime startInclusive = date.atStartOfDay();
            LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();
            weeklyPoints.add(AdminWeeklyAnalysisPoint.builder()
                    .label(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN))
                    .date(date.toString())
                    .requestedCount(analysisRequestRepository.countRequestedBetween(startInclusive, endExclusive))
                    .completedCount(analysisRequestRepository.countCompletedBetween(startInclusive, endExclusive))
                    .build());
        }

        AdminRiskDistribution riskDistribution = AdminRiskDistribution.builder()
                .safeCount(analysisResultRepository.countByRiskScoreRange(0, 49.999))
                .cautionCount(analysisResultRepository.countByRiskScoreRange(50, 79.999))
                .dangerCount(analysisResultRepository.countByRiskScoreRange(80, 100))
                .build();

        return AdminAnalysisStatsResponse.builder()
                .weeklyTotalCount(weeklyTotalCount)
                .deepfakeDetectionRate(deepfakeDetectionRate)
                .averageAnalysisMinutes(averageAnalysisMinutes)
                .weeklyPoints(weeklyPoints)
                .riskDistribution(riskDistribution)
                .build();
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double calculateAverageAnalysisMinutes(List<AnalysisRequest> completedRequests) {
        if (completedRequests.isEmpty()) {
            return 0.0;
        }

        long totalSeconds = 0;
        int counted = 0;
        for (AnalysisRequest request : completedRequests) {
            LocalDateTime completedAt = request.getCompletedAt();
            LocalDateTime startedAt = request.getStartedAt() != null
                    ? request.getStartedAt()
                    : request.getRequestedAt();
            if (completedAt == null || startedAt == null || !completedAt.isAfter(startedAt)) {
                continue;
            }
            totalSeconds += Duration.between(startedAt, completedAt).getSeconds();
            counted += 1;
        }

        if (counted == 0) {
            return 0.0;
        }

        return roundOneDecimal((totalSeconds / 60.0) / counted);
    }
}
