package com.example.demo.controller;

import com.example.demo.config.OpenApiConfig;
import com.example.demo.dto.AnalysisTrendResponse;
import com.example.demo.dto.DashboardIntroResponse;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.dto.RecentAnalysisResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.dashboard.DashboardIntroService;
import com.example.demo.service.dashboard.EvidenceStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evidence Dashboard", description = "증거 대시보드 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceDashboardController {

    private final DashboardIntroService dashboardIntroService;
    private final EvidenceStatsService evidenceStatsService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "서비스 소개 및 바로가기", description = "RQ-DSH-041: 메인 대시보드 히어로 배너·CTA·핵심 가치 카드 문구를 조회합니다.")
    @GetMapping("/dashboard/intro")
    public DashboardIntroResponse dashboardIntro() {
        authUserResolver.requireCurrentUser();
        return dashboardIntroService.getIntro();
    }

    @Operation(summary = "대시보드 통계", description = "RQ-DSH-043: 총 분석·딥페이크 탐지·완료·처리 중 건수를 조회합니다.")
    @GetMapping("/stats")
    public EvidenceStatsResponse stats() {
        return evidenceStatsService.getDashboardStats(
                authUserResolver.requireCurrentUser().getUserId()
        );
    }

    @Operation(summary = "최근 분석 추이", description = "RQ-DSH-044: 최근 N일 일별 완료 분석 건수(꺾은선 차트용)를 조회합니다.")
    @GetMapping("/stats/trend")
    public AnalysisTrendResponse analysisTrend(
            @Parameter(description = "조회 일수 (1~30, 기본 7)") @RequestParam(defaultValue = "7") int days
    ) {
        return evidenceStatsService.getAnalysisTrend(
                authUserResolver.requireCurrentUser().getUserId(),
                days
        );
    }

    @Operation(summary = "최근 분석 이력", description = "RQ-DSH-045: 대시보드 위젯용 최근 분석 요청 목록(3~5건)을 조회합니다.")
    @GetMapping("/stats/recent")
    public RecentAnalysisResponse recentAnalyses(
            @Parameter(description = "조회 건수 (3~5, 기본 5)") @RequestParam(defaultValue = "5") int limit
    ) {
        return evidenceStatsService.getRecentAnalyses(
                authUserResolver.requireCurrentUser().getUserId(),
                limit
        );
    }
}
