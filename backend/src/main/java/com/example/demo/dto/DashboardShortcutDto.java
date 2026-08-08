package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardShortcutDto {

    /** 버튼 라벨 (예: 분석 시작하기) */
    private String label;

    /** IN_APP: 앱 내 해시·뷰 전환, ROUTE: 경로 이동 */
    private String actionType;

    /** IN_APP → #new-analysis, ROUTE → /compare */
    private String actionTarget;

    /** primary | outline */
    private String variant;
}
