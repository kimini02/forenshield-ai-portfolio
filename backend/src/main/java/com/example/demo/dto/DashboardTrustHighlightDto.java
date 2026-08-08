package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardTrustHighlightDto {

    /** 핵심 가치 라벨 (예: CoC 감사 추적) */
    private String label;

    /** FE 아이콘 매핑 키 (history, check-circle, layers) */
    private String iconKey;
}
