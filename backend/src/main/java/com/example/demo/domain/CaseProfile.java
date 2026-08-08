package com.example.demo.domain;

import com.example.demo.domain.enums.CaseReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "case_profiles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"uploader_id", "case_key"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_profile_id")
    private Long caseProfileId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "case_key", nullable = false, length = 255)
    private String caseKey;

    @Column(name = "representative_evidence_id")
    private Long representativeEvidenceId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 40)
    private CaseReviewStatus reviewStatus = CaseReviewStatus.NONE;

    @Column(name = "review_requested_at")
    private LocalDateTime reviewRequestedAt;

    @Column(name = "review_request_memo", length = 500)
    private String reviewRequestMemo;

    @Column(name = "reviewer_comment", length = 500)
    private String reviewerComment;

    @Column(name = "review_approved_at")
    private LocalDateTime reviewApprovedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CaseProfile(Long uploaderId, String caseKey, Long representativeEvidenceId) {
        this.uploaderId = uploaderId;
        this.caseKey = caseKey;
        this.representativeEvidenceId = representativeEvidenceId;
        this.assigneeId = uploaderId;
        this.reviewStatus = CaseReviewStatus.NONE;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRepresentativeEvidence(Long representativeEvidenceId) {
        this.representativeEvidenceId = representativeEvidenceId;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCaseKey(String caseKey) {
        this.caseKey = caseKey;
        this.updatedAt = LocalDateTime.now();
    }

    public void assignReviewer(Long reviewerId) {
        this.reviewerId = reviewerId;
        this.reviewStatus = CaseReviewStatus.REVIEW_ASSIGNED;
        this.updatedAt = LocalDateTime.now();
    }

    public void requestReview(String memo) {
        if (this.reviewStatus != CaseReviewStatus.NONE) {
            throw new IllegalStateException("Review already requested or in progress");
        }
        this.reviewStatus = CaseReviewStatus.REVIEW_REQUESTED;
        this.reviewRequestedAt = LocalDateTime.now();
        this.reviewRequestMemo = memo;
        this.reviewerComment = null;
        this.reviewApprovedAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateReviewerComment(String reviewerComment) {
        this.reviewerComment = reviewerComment;
        this.updatedAt = LocalDateTime.now();
    }

    public void reopenReviewForNewEvidence() {
        if (this.reviewerId == null) {
            return;
        }
        this.reviewStatus = CaseReviewStatus.REVIEW_ASSIGNED;
        this.reviewRequestedAt = LocalDateTime.now();
        this.reviewerComment = null;
        this.reviewApprovedAt = null;
        this.updatedAt = this.reviewRequestedAt;
    }

    public void approveReview() {
        this.reviewStatus = CaseReviewStatus.REPORT_APPROVED;
        this.reviewApprovedAt = LocalDateTime.now();
        this.updatedAt = this.reviewApprovedAt;
    }

    public void requestRevision() {
        this.reviewStatus = CaseReviewStatus.REVIEW_SUPPLEMENT_REQUESTED;
        this.reviewApprovedAt = null;
        this.updatedAt = LocalDateTime.now();
    }
}
