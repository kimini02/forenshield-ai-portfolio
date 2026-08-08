package com.example.demo.dto.mypage;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AnalysisHistoryPageResponse {

	private List<CaseSummaryResponse> content;
	private int page;
	private int size;
	private long totalElements;
	private int totalPages;
}
