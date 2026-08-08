package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEvidenceAnalysisResponse {

    private String id;
    private String status;
    private String requestedAt;
    private String completedAt;
}
