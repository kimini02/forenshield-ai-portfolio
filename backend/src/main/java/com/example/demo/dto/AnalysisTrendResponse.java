package com.example.demo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisTrendResponse {

    /** 조회 기간(일). 요청 파라미터 days 와 동일 */
    private int days;

    /** 시작일~종료일(오늘)까지 일별 집계. days 개의 항목 */
    private List<AnalysisTrendPoint> points;
}
