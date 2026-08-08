package com.example.demo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentAnalysisResponse {

    /** 요청 limit (3~5) */
    private int limit;

    /** 최근 분석 요청 순 (증거당 최신 1건, 최대 limit개) */
    private List<RecentAnalysisItem> items;
}
