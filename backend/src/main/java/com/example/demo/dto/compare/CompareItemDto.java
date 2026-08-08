package com.example.demo.dto.compare;

import com.example.demo.domain.enums.CompareItemResult;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompareItemDto {

    private String itemKey;
    private String label;
    private String originalValue;
    private String candidateValue;
    private CompareItemResult result;
}
