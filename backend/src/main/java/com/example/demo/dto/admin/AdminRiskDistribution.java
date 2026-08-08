package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminRiskDistribution {

    private long safeCount;
    private long cautionCount;
    private long dangerCount;
}
