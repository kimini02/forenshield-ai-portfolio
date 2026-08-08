package com.example.demo.service.evidence;

import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.evidence.hls.EvidenceHlsLookupService;
import com.example.demo.service.report.ReportPdfService;
import com.example.demo.util.UserScopeSupport;
import com.example.demo.util.UserRoleSupport;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CaseReviewService {

    private static final Set<CaseReviewStatus> ASSIGNABLE_STATUSES = EnumSet.of(
            CaseReviewStatus.REVIEW_REQUESTED,
            CaseReviewStatus.REVIEW_ASSIGNED
    );

    private final CaseAccessService caseAccessService;
    private final CaseProfileRepository caseProfileRepository;
    private final UserRepository userRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final CaseDetailAssembler caseDetailAssembler;
    private final CaseEvidencePresentationService caseEvidencePresentationService;
    private final EvidenceHlsLookupService evidenceHlsLookupService;
    private final ReportPdfService reportPdfService;

    @Transactional
    public CaseDetailResponse requestReview(User user, String caseKey, String memo) {
        CaseAccessService.CaseAccessContext context = caseAccessService.requireOwnedOrOrgCase(user, caseKey);
        if (!UserRoleSupport.isOrgAdmin(user.getRole()) && !context.uploaderId().equals(user.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "본인 사건만 검토 요청할 수 있습니다.");
        }
        if (!isCaseCompleted(context)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "CASE_NOT_COMPLETED", "분석이 완료된 사건만 검토 요청할 수 있습니다.");
        }

        CaseProfile profile = resolveProfile(context);
        if (profile.getReviewStatus() != CaseReviewStatus.NONE) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "REVIEW_ALREADY_REQUESTED", "이미 검토가 요청되었거나 진행 중입니다.");
        }

        profile.requestReview(normalizeMemo(memo));
        caseProfileRepository.save(profile);
        return assembleDetail(user, context, profile);
    }

    @Transactional
    public CaseDetailResponse assignReviewer(User user, String caseKey, String reviewerIdValue) {
        CaseAccessService.CaseAccessContext context = caseAccessService.requireOrgCase(user, caseKey);
        User reviewer = resolveReviewer(user, reviewerIdValue);

        CaseProfile profile = resolveProfile(context);
        if (!ASSIGNABLE_STATUSES.contains(profile.getReviewStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "REVIEW_NOT_REQUESTED", "검토 요청된 사건만 검토자를 배정할 수 있습니다.");
        }
        ensureReviewerInSameDepartment(context.uploaderId(), reviewer);

        profile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(profile);
        return assembleDetail(user, context, profile);
    }

    @Transactional
    public CaseDetailResponse recordDecision(User user, String caseKey, String decision, String memo) {
        CaseAccessService.CaseAccessContext context = caseAccessService.requireAccessibleCase(user, caseKey);
        CaseProfile profile = resolveProfile(context);
        if (profile.getReviewerId() == null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "REVIEW_NOT_ASSIGNED", "검토자가 배정된 사건만 결정할 수 있습니다.");
        }
        if (!canRecordDecision(user, profile)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "배정된 검토자만 결정할 수 있습니다.");
        }

        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase();
        profile.updateReviewerComment(normalizeMemo(memo));
        switch (normalizedDecision) {
            case "APPROVED" -> profile.approveReview();
            case "REVISION" -> profile.requestRevision();
            default -> throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_DECISION", "decision은 APPROVED 또는 REVISION이어야 합니다.");
        }

        caseProfileRepository.save(profile);
        if ("APPROVED".equals(normalizedDecision)) {
            reportPdfService.issueCaseReports(user, context.evidences());
        }
        return assembleDetail(user, context, profile);
    }

    private CaseProfile resolveProfile(CaseAccessService.CaseAccessContext context) {
        if (context.profile() != null) {
            return context.profile();
        }
        return caseProfileRepository.save(new CaseProfile(context.uploaderId(), context.caseKey(), null));
    }

    private boolean isCaseCompleted(CaseAccessService.CaseAccessContext context) {
        List<Long> evidenceIds = context.evidences().stream().map(evidence -> evidence.getEvidenceId()).toList();
        if (evidenceIds.isEmpty()) {
            return false;
        }
        return caseDetailAssembler.resolveAggregateStatus(
                context.evidences(),
                analysisRequestRepository.findByEvidenceIdInOrderByRequestedAtDesc(evidenceIds)
        ).equals("COMPLETED");
    }

    private User resolveReviewer(User admin, String reviewerIdValue) {
        if (reviewerIdValue == null || reviewerIdValue.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "reviewerId는 필수입니다.");
        }
        Long reviewerId;
        try {
            reviewerId = Long.parseLong(reviewerIdValue.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "reviewerId 형식이 올바르지 않습니다.");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "REVIEWER_NOT_FOUND", "검토자 계정을 찾을 수 없습니다."));
        if (!UserRoleSupport.isReviewer(reviewer.getRole())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_REVIEWER", "검토자 역할 계정만 배정할 수 있습니다.");
        }
        if (reviewer.getStatus() != UserStatus.APPROVED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_REVIEWER", "승인된 검토자만 배정할 수 있습니다.");
        }
        return reviewer;
    }

    private void ensureReviewerInSameDepartment(Long uploaderId, User reviewer) {
        User uploader = userRepository.findByUserIdAndDeletedAtIsNull(uploaderId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건 담당 분석관을 찾을 수 없습니다."));
        if (!UserScopeSupport.isSameOrganizationAndDepartment(uploader, reviewer)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEWER_SCOPE",
                    "사건 담당 분석관과 같은 기관/부서의 검토자만 배정할 수 있습니다."
            );
        }
    }

    private boolean canRecordDecision(User user, CaseProfile profile) {
        if (UserRoleSupport.isOrgAdmin(user.getRole())) {
            return true;
        }
        return UserRoleSupport.isReviewer(user.getRole())
                && profile.getReviewerId() != null
                && profile.getReviewerId().equals(user.getUserId());
    }

    private CaseDetailResponse assembleDetail(
            User user,
            CaseAccessService.CaseAccessContext context,
            CaseProfile profile
    ) {
        List<Long> evidenceIds = context.evidences().stream().map(evidence -> evidence.getEvidenceId()).toList();
        User uploader = userRepository.findByUserIdAndDeletedAtIsNull(context.uploaderId()).orElse(user);
        var hlsByEvidenceId = evidenceHlsLookupService.findByEvidenceIds(evidenceIds);
        return caseDetailAssembler.assemble(
                uploader,
                context.caseKey(),
                context.evidences(),
                analysisRequestRepository.findByEvidenceIdInOrderByRequestedAtDesc(evidenceIds),
                profile,
                uploader,
                hlsByEvidenceId
        );
    }

    private String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        return memo.trim();
    }
}
