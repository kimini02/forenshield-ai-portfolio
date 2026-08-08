package com.example.demo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardIntroResponse {

    /** RQ-DSH-041: 상단 배지 문구 */
    private String badgeLabel;

    /** 히어로 제목 1행 */
    private String titleLine1;

    /** 히어로 제목 2행 */
    private String titleLine2;

    /** 서비스 소개 본문 */
    private String description;

    /** 분석 시작하기 · 비교 검증 등 CTA */
    private List<DashboardShortcutDto> shortcuts;

    /** CoC · SHA-256 · 딥페이크 등 신뢰/역량 하이라이트 */
    private List<DashboardTrustHighlightDto> trustHighlights;
}
