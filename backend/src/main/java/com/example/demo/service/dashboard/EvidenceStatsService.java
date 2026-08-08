package com.example.demo.service.dashboard;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.AnalysisTrendPoint;
import com.example.demo.dto.AnalysisTrendResponse;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.dto.RecentAnalysisItem;
import com.example.demo.dto.RecentAnalysisResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceStatsService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 30;
    private static final int MIN_RECENT_LIMIT = 3;
    private static final int MAX_RECENT_LIMIT = 5;

    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final EvidenceRepository evidenceRepository;
    private final DashboardStatsCache dashboardStatsCache;

    /** RQ-DSH-043 / RQ-PER-155: 대시보드 통계 카드 4종 (30초 캐시) */
    public EvidenceStatsResponse getDashboardStats(Long uploaderId) {
        EvidenceStatsResponse cached = dashboardStatsCache.get(uploaderId);
        if (cached != null) {
            return cached;
        }

        EvidenceStatsResponse response = EvidenceStatsResponse.builder()
                .totalAnalysisCount(analysisRequestRepository.countTotalByUploader(uploaderId))
                .deepfakeDetectedCount(analysisRequestRepository.countDeepfakeDetectedByUploader(uploaderId))
                .completedCount(analysisRequestRepository.countByUploaderAndStatus(
                        uploaderId, AnalysisStatus.COMPLETED))
                .inProgressCount(analysisRequestRepository.countByUploaderAndStatusIn(
                        uploaderId, List.of(AnalysisStatus.QUEUED, AnalysisStatus.ANALYZING)))
                .build();
        dashboardStatsCache.put(uploaderId, response);
        return response;
    }

    /** RQ-DSH-044: 최근 N일 일별 완료 분석 건수 (꺾은선 차트용) */
    public AnalysisTrendResponse getAnalysisTrend(Long uploaderId, int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "days는 " + MIN_DAYS + "~" + MAX_DAYS + " 사이여야 합니다.");
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> completedByDate = analysisRequestRepository
                .findCompletedByUploaderCompletedAtBetween(uploaderId, rangeStart, rangeEnd)
                .stream()
                .filter(request -> request.getCompletedAt() != null)
                .collect(Collectors.groupingBy(
                        request -> request.getCompletedAt().toLocalDate(),
                        Collectors.counting()
                ));

        List<AnalysisTrendPoint> points = new ArrayList<>(days);
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            points.add(AnalysisTrendPoint.builder()
                    .date(date.toString())
                    .completedCount(completedByDate.getOrDefault(date, 0L))
                    .build());
        }

        return AnalysisTrendResponse.builder()
                .days(days)
                .points(points)
                .build();
    }

    /** RQ-DSH-045: 대시보드 최근 분석 이력 위젯 (증거당 최신 요청 1건, 3~5개) */
    public RecentAnalysisResponse getRecentAnalyses(Long uploaderId, int limit) {
        if (limit < MIN_RECENT_LIMIT || limit > MAX_RECENT_LIMIT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "limit는 " + MIN_RECENT_LIMIT + "~" + MAX_RECENT_LIMIT + " 사이여야 합니다.");
        }

        List<AnalysisRequest> candidates = analysisRequestRepository.findRecentByUploader(
                uploaderId,
                PageRequest.of(0, limit * 3));

        LinkedHashMap<Long, AnalysisRequest> latestByEvidence = new LinkedHashMap<>();
        for (AnalysisRequest request : candidates) {
            latestByEvidence.putIfAbsent(request.getEvidenceId(), request);
            if (latestByEvidence.size() >= limit) {
                break;
            }
        }

        List<AnalysisRequest> selected = new ArrayList<>(latestByEvidence.values());
        if (selected.isEmpty()) {
            return RecentAnalysisResponse.builder()
                    .limit(limit)
                    .items(List.of())
                    .build();
        }

        List<Long> evidenceIds = selected.stream().map(AnalysisRequest::getEvidenceId).toList();
        List<Long> requestIds = selected.stream().map(AnalysisRequest::getAnalysisRequestId).toList();

        Map<Long, Evidence> evidenceById = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, uploaderId)
                .stream()
                .collect(Collectors.toMap(Evidence::getEvidenceId, Function.identity()));

        Map<Long, AnalysisResult> resultByRequestId = analysisResultRepository
                .findByAnalysisRequestIdIn(requestIds)
                .stream()
                .collect(Collectors.toMap(AnalysisResult::getAnalysisRequestId, Function.identity()));

        List<RecentAnalysisItem> items = selected.stream()
                .map(request -> toRecentItem(
                        request,
                        evidenceById.get(request.getEvidenceId()),
                        resultByRequestId.get(request.getAnalysisRequestId())))
                .toList();

        return RecentAnalysisResponse.builder()
                .limit(limit)
                .items(items)
                .build();
    }

    private RecentAnalysisItem toRecentItem(
            AnalysisRequest request,
            Evidence evidence,
            AnalysisResult result
    ) {
        Double riskScore = null;
        String riskLevel = null;
        String verdictIndicator = null;

        if (request.getStatus() == AnalysisStatus.COMPLETED && result != null) {
            riskScore = result.getRiskScore();
            if (result.getRiskLevel() != null) {
                riskLevel = result.getRiskLevel().name();
                verdictIndicator = toVerdictIndicator(result.getRiskLevel());
            }
        }

        return RecentAnalysisItem.builder()
                .evidenceId(request.getEvidenceId())
                .caseId(evidence != null ? EvidenceCaseIdResolver.resolve(evidence) : null)
                .caseName(evidence != null ? evidence.getCaseName() : null)
                .analysisRequestId(request.getAnalysisRequestId())
                .fileName(evidence != null ? evidence.getFileName() : null)
                .requestedAt(ApiDateTimeFormatter.formatUtc(request.getRequestedAt()))
                .status(toApiStatus(request.getStatus()))
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .verdictIndicator(verdictIndicator)
                .build();
    }

    private String toApiStatus(AnalysisStatus status) {
        return switch (status) {
            case QUEUED -> "PENDING";
            case ANALYZING -> "PROCESSING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }

    private String toVerdictIndicator(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "NORMAL";
            case MEDIUM -> "SUSPICIOUS";
            case HIGH -> "DANGER";
        };
    }
}
