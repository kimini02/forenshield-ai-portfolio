package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CaseDetailResponse {

    private String caseId;
    private String caseName;
    private String status;
    private String createdAt;
    private Long representativeEvidenceId;
    private String createdBy;
    private String assigneeId;
    private String reviewerId;
    private String reviewStatus;
    private List<CaseEvidenceSummaryDto> evidences;
    private String organizationId;
    private String reviewRequestedAt;
    private String reviewerComment;
}
