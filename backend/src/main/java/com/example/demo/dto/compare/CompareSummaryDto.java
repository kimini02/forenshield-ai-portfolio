package com.example.demo.dto.compare;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompareSummaryDto {

    private int matchCount;
    private int mismatchCount;
    private int skippedCount;
    private String verdictLabel;
}
