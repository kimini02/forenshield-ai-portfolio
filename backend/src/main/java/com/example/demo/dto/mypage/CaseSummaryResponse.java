package com.example.demo.dto.mypage;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseSummaryResponse {

	private String caseId;
	private String caseName;
	/** PENDING · PROCESSING · COMPLETED · FAILED */
	private String status;
	private String createdAt;
	private int evidenceCount;
	private String representativeFileName;
	private Long representativeEvidenceId;
	private String representativeEvidenceLabel;
	private Double riskScore;
	private String organizationId;
	private String department;
	private String createdBy;
	private String assigneeId;
	private String reviewerId;
	private String reviewStatus;
	private String aiResult;
	private String reviewRequestedAt;
}
