package com.example.demo.controller;

import com.example.demo.dto.admin.AdminAnalysisStatsResponse;
import com.example.demo.dto.admin.AdminDashboardStatsResponse;
import com.example.demo.service.admin.AdminAnalysisStatsService;
import com.example.demo.service.admin.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminAnalysisStatsService adminAnalysisStatsService;

    @Operation(summary = "대시보드 통계 조회")
    @GetMapping("/stats")
    public AdminDashboardStatsResponse getStats() {
        return adminDashboardService.getStats();
    }

    @Operation(summary = "분석 통계 조회", description = "RQ-ADMIN-150 · 관리자 통계 분석 화면")
    @GetMapping("/analysis-stats")
    public AdminAnalysisStatsResponse getAnalysisStats() {
        return adminAnalysisStatsService.getStats();
    }
}
