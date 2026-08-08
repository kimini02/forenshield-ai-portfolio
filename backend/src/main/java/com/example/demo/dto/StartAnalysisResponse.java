package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StartAnalysisResponse {

    private boolean success;
    private String message;
    private String caseName;
    private int startedCount;
    private List<Long> evidenceIds;
    /** 증거별 큐 등록 결과 (SK-921) */
    private List<AnalysisStartResultItem> results;
}
