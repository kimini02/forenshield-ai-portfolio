package com.example.demo.dto.report;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportListPageResponse {

    private List<ReportSummaryDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
