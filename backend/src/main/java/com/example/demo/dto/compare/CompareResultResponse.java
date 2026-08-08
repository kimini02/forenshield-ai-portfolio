package com.example.demo.dto.compare;

import com.example.demo.domain.enums.CompareItemResult;
import com.example.demo.domain.enums.CompareVerdict;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CompareResultResponse {

    private Long compareId;
    private Long originalEvidenceId;
    private String candidateFileName;
    private CompareVerdict verdict;
    private CompareSummaryDto summary;
    private List<CompareItemDto> items;
    private CompareSignatureInfoDto signature;
    private CompareBlockchainInfoDto blockchain;
    private String createdAt;
}
