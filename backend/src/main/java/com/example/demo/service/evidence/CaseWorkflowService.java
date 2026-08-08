package com.example.demo.service.evidence;

import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceLifecycleStatus;
import com.example.demo.domain.enums.EvidenceRole;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.caseworkflow.AssignCaseReviewerRequest;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.util.CaseKeyNormalizer;
import com.example.demo.util.EvidenceCaseIdResolver;
import com.example.demo.util.UserScopeSupport;
import com.example.demo.util.UserRoleSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CaseWorkflowService {

    private final EvidenceRepository evidenceRepository;
    private final CaseProfileRepository caseProfileRepository;
    private final UserRepository userRepository;
    private final EvidenceAccessService evidenceAccessService;
    private final FileService fileService;
    private final CustodyLogService custodyLogService;
    private final CaseEvidencePresentationService caseEvidencePresentationService;

    @Transactional
    public String createCase(User user, String caseName) {
        if (!UserRoleSupport.isInvestigator(user.getRole()) && !UserRoleSupport.isOrgAdmin(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "사건을 생성할 권한이 없습니다.");
        }

        String trimmedName = caseName == null ? "" : caseName.trim();
        if (trimmedName.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건명을 입력해 주세요.");
        }

        String caseKey = CaseKeyNormalizer.requireCaseKey(trimmedName);
        assertNoDuplicateCase(user.getUserId(), caseKey);
        caseProfileRepository.save(new CaseProfile(user.getUserId(), caseKey, null));
        return caseKey;
    }

    @Transactional
    public String renameCase(User user, String caseKey, String newCaseName) {
        String oldKey = CaseKeyNormalizer.requireCaseKey(caseKey);
        String trimmedName = newCaseName == null ? "" : newCaseName.trim();
        if (trimmedName.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건명을 입력해 주세요.");
        }
        if (oldKey.equalsIgnoreCase(trimmedName)) {
            return oldKey;
        }

        List<Evidence> caseEvidences = loadCaseEvidences(user, oldKey);
        var existingProfile = caseProfileRepository.findByUploaderIdAndCaseKey(user.getUserId(), oldKey);
        if (caseEvidences.isEmpty() && existingProfile.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다.");
        }

        assertNoDuplicateCase(user.getUserId(), trimmedName, oldKey);

        if (caseEvidences.isEmpty()) {
            existingProfile.get().updateCaseKey(trimmedName);
            caseProfileRepository.save(existingProfile.get());
            return trimmedName;
        }

        for (Evidence evidence : caseEvidences) {
            evidence.renameCase(oldKey, trimmedName);
            evidenceRepository.save(evidence);
        }

        migrateCaseProfile(user, oldKey, trimmedName);

        Evidence anchorEvidence = caseEvidences.get(0);
        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.EVIDENCE,
                anchorEvidence.getEvidenceId(),
                "CASE_RENAMED",
                anchorEvidence.getOriginalHashValue(),
                anchorEvidence.getOriginalStoragePath(),
                "사건명 변경: " + oldKey + " → " + trimmedName,
                "{\"oldCaseKey\":\"" + escapeJson(oldKey) + "\",\"newCaseKey\":\""
                        + escapeJson(trimmedName) + "\"}",
                null
        );

        return trimmedName;
    }

    @Transactional
    public void excludeEvidence(User user, Long evidenceId, String reason) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        if (!evidence.isWorkflowActive()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "EVIDENCE_NOT_ACTIVE", "이미 사용 제외되었거나 대체된 증거입니다.");
        }

        String caseKey = EvidenceCaseIdResolver.resolve(evidence);
        List<Evidence> caseEvidences = loadCaseEvidences(user, caseKey);
        Long representativeId = caseEvidencePresentationService
                .resolveRepresentativeEvidenceId(user, caseKey, caseEvidences)
                .orElse(null);

        if (Objects.equals(representativeId, evidenceId) && countActiveEvidences(caseEvidences) > 1) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPRESENTATIVE_EVIDENCE",
                    "대표 증거는 사용 제외할 수 없습니다. 대표 증거를 먼저 변경해 주세요.");
        }

        String normalizedReason = normalizeReason(reason, "사용자 요청으로 사용 제외 처리되었습니다.");
        evidence.exclude(normalizedReason);
        evidenceRepository.save(evidence);

        if (Objects.equals(representativeId, evidenceId)) {
            clearRepresentative(user, caseKey);
            promoteFallbackRepresentative(user, caseKey, caseEvidences, evidenceId);
        }

        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.EVIDENCE,
                evidenceId,
                "EVIDENCE_EXCLUDED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                normalizedReason,
                null,
                null
        );
    }

    @Transactional
    public void setRepresentativeEvidence(User user, String caseKey, Long evidenceId) {
        String normalizedCaseKey = CaseKeyNormalizer.requireCaseKey(caseKey);
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        String evidenceCaseKey = EvidenceCaseIdResolver.resolve(evidence);
        if (!normalizedCaseKey.equals(evidenceCaseKey)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "CASE_MISMATCH", "선택한 증거가 해당 사건에 속하지 않습니다.");
        }
        if (!evidence.isWorkflowActive()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "EVIDENCE_NOT_ACTIVE", "사용 제외되었거나 대체된 증거는 대표로 지정할 수 없습니다.");
        }

        upsertRepresentative(user, normalizedCaseKey, evidenceId);
        syncPrimaryRole(user, normalizedCaseKey, evidenceId);
    }

    @Transactional
    public Long assignReviewer(User actor, String caseKey, AssignCaseReviewerRequest request) {
        if (!UserRoleSupport.isOrgAdmin(actor.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "검토자 배정 권한이 없습니다.");
        }

        String normalizedCaseKey = CaseKeyNormalizer.requireCaseKey(caseKey);
        Long reviewerId = parseUserId(request.getReviewerId(), "reviewerId");
        User reviewer = userRepository.findByUserIdAndDeletedAtIsNull(reviewerId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "REVIEWER_NOT_FOUND", "검토자를 찾을 수 없습니다."));
        if (reviewer.getRole() != UserRole.ROLE_REVIEWER) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REVIEWER", "검토자 역할 사용자만 배정할 수 있습니다.");
        }
        if (reviewer.getStatus() != UserStatus.APPROVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REVIEWER", "승인된 검토자만 배정할 수 있습니다.");
        }

        Long uploaderId = resolveCaseOwnerForAssignment(
                actor,
                normalizedCaseKey,
                parseOptionalUserId(request.getUploaderId())
        );
        User uploader = userRepository.findByUserIdAndDeletedAtIsNull(uploaderId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다."));
        ensureSameOrganization(actor, uploader);
        ensureReviewerInSameDepartment(uploader, reviewer);

        CaseProfile profile = caseProfileRepository.findByUploaderIdAndCaseKey(uploaderId, normalizedCaseKey)
                .orElseGet(() -> createProfileFromExistingEvidences(uploaderId, normalizedCaseKey));
        profile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(profile);
        return uploaderId;
    }

    private CaseProfile createProfileFromExistingEvidences(Long uploaderId, String caseKey) {
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(uploaderId, caseKey);
        if (evidences.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다.");
        }
        Long representativeEvidenceId = evidences.get(0).getEvidenceId();
        return caseProfileRepository.save(new CaseProfile(uploaderId, caseKey, representativeEvidenceId));
    }

    private Long resolveCaseOwnerForAssignment(User actor, String normalizedCaseKey, Long requestedUploaderId) {
        if (requestedUploaderId != null) {
            return requestedUploaderId;
        }
        if (actor.getRole() == UserRole.ROLE_ADMIN) {
            return resolveUniqueOwnerGlobally(normalizedCaseKey);
        }
        return resolveUniqueOwnerInOrganization(
                userRepository.findUserIdsByOrganizationType(actor.getOrganizationType()),
                normalizedCaseKey
        );
    }

    private Long resolveUniqueOwnerInOrganization(List<Long> orgUploaderIds, String normalizedCaseKey) {
        Set<Long> ownerIds = collectOwnerIds(orgUploaderIds, normalizedCaseKey);
        if (ownerIds.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다.");
        }
        if (ownerIds.size() > 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "UPLOADER_ID_REQUIRED",
                    "동일 사건명이 여러 건 존재합니다. uploaderId를 지정해 주세요."
            );
        }
        return ownerIds.iterator().next();
    }

    private Long resolveUniqueOwnerGlobally(String normalizedCaseKey) {
        Set<Long> ownerIds = new HashSet<>();
        for (CaseProfile profile : caseProfileRepository.findByCaseKey(normalizedCaseKey)) {
            ownerIds.add(profile.getUploaderId());
        }
        for (Evidence evidence : evidenceRepository.findByCaseKey(normalizedCaseKey)) {
            ownerIds.add(evidence.getUploaderId());
        }
        if (ownerIds.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다.");
        }
        if (ownerIds.size() > 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "UPLOADER_ID_REQUIRED",
                    "동일 사건명이 여러 건 존재합니다. uploaderId를 지정해 주세요."
            );
        }
        return ownerIds.iterator().next();
    }

    private Set<Long> collectOwnerIds(List<Long> orgUploaderIds, String normalizedCaseKey) {
        Set<Long> ownerIds = new HashSet<>();
        for (Long orgUploaderId : orgUploaderIds) {
            if (caseProfileRepository.findByUploaderIdAndCaseKey(orgUploaderId, normalizedCaseKey).isPresent()) {
                ownerIds.add(orgUploaderId);
            }
            if (!evidenceRepository.findByUploaderIdAndCaseKey(orgUploaderId, normalizedCaseKey).isEmpty()) {
                ownerIds.add(orgUploaderId);
            }
        }
        return ownerIds;
    }

    private void ensureSameOrganization(User actor, User target) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (!Objects.equals(actor.getOrganizationType(), target.getOrganizationType())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "같은 기관 사용자만 배정할 수 있습니다.");
        }
    }

    private void ensureReviewerInSameDepartment(User uploader, User reviewer) {
        if (!UserScopeSupport.isSameOrganizationAndDepartment(uploader, reviewer)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEWER_SCOPE",
                    "사건 담당 분석관과 같은 기관/부서의 검토자만 배정할 수 있습니다."
            );
        }
    }

    private Long parseOptionalUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseUserId(raw, "uploaderId");
    }

    private Long parseUserId(String raw, String fieldName) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USER_ID",
                    fieldName + " must be a numeric user id."
            );
        }
    }

    @Transactional
    public void setEvidenceRole(User user, Long evidenceId, EvidenceRole role) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        if (!evidence.isWorkflowActive()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "EVIDENCE_NOT_ACTIVE", "사용 제외되었거나 대체된 증거의 역할은 변경할 수 없습니다.");
        }

        String caseKey = EvidenceCaseIdResolver.resolve(evidence);
        evidence.assignRole(role);
        evidenceRepository.save(evidence);

        if (role == EvidenceRole.PRIMARY) {
            upsertRepresentative(user, caseKey, evidenceId);
            syncPrimaryRole(user, caseKey, evidenceId);
        } else if (caseProfileRepository.findByUploaderIdAndCaseKey(user.getUserId(), caseKey)
                .map(CaseProfile::getRepresentativeEvidenceId)
                .filter(id -> Objects.equals(id, evidenceId))
                .isPresent()) {
            clearRepresentative(user, caseKey);
        }
    }

    @Transactional
    public FileUploadResponse replaceEvidence(
            User user,
            Long oldEvidenceId,
            MultipartFile file,
            String reason
    ) {
        Evidence oldEvidence = evidenceAccessService.requireOwned(user, oldEvidenceId);
        if (!oldEvidence.isWorkflowActive()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "EVIDENCE_NOT_ACTIVE", "이미 사용 제외되었거나 대체된 증거입니다.");
        }

        String caseName = oldEvidence.getCaseName();
        if (caseName == null || caseName.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건명이 없는 증거는 대체할 수 없습니다.");
        }

        FileUploadResponse uploadResponse = fileService.upload(
                file, caseName, oldEvidence.getCaseNumber(), user.getUserId());
        Evidence replacement = evidenceRepository.findById(uploadResponse.getEvidenceId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "REPLACEMENT_FAILED", "대체 증거 등록에 실패했습니다."));

        String normalizedReason = normalizeReason(reason, "새 증거로 대체 등록되었습니다.");
        oldEvidence.markReplaced(replacement.getEvidenceId(), normalizedReason);
        replacement.assignRole(EvidenceRole.SUPPLEMENT);
        assignDisplayLabelIfMissing(replacement, loadCaseEvidences(user, EvidenceCaseIdResolver.resolve(oldEvidence)));
        evidenceRepository.save(oldEvidence);
        evidenceRepository.save(replacement);

        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.EVIDENCE,
                oldEvidenceId,
                "EVIDENCE_REPLACED",
                oldEvidence.getOriginalHashValue(),
                oldEvidence.getOriginalStoragePath(),
                normalizedReason,
                "{\"replacementEvidenceId\":" + replacement.getEvidenceId() + "}",
                null
        );

        return uploadResponse;
    }

    private void promoteFallbackRepresentative(
            User user,
            String caseKey,
            List<Evidence> caseEvidences,
            Long excludedEvidenceId
    ) {
        caseEvidences.stream()
                .filter(Evidence::isWorkflowActive)
                .filter(evidence -> !Objects.equals(evidence.getEvidenceId(), excludedEvidenceId))
                .map(Evidence::getEvidenceId)
                .min(Long::compareTo)
                .ifPresent(fallbackId -> setRepresentativeEvidence(user, caseKey, fallbackId));
    }

    private void syncPrimaryRole(User user, String caseKey, Long primaryEvidenceId) {
        for (Evidence caseEvidence : loadCaseEvidences(user, caseKey)) {
            if (caseEvidence.getEvidenceId().equals(primaryEvidenceId)) {
                caseEvidence.assignRole(EvidenceRole.PRIMARY);
            } else if (caseEvidence.getEvidenceRole() == EvidenceRole.PRIMARY) {
                caseEvidence.assignRole(EvidenceRole.SUPPLEMENT);
            }
            evidenceRepository.save(caseEvidence);
        }
    }

    private void upsertRepresentative(User user, String caseKey, Long evidenceId) {
        caseProfileRepository.findByUploaderIdAndCaseKey(user.getUserId(), caseKey)
                .ifPresentOrElse(
                        profile -> {
                            profile.updateRepresentativeEvidence(evidenceId);
                            caseProfileRepository.save(profile);
                        },
                        () -> caseProfileRepository.save(new CaseProfile(user.getUserId(), caseKey, evidenceId))
                );
    }

    private void clearRepresentative(User user, String caseKey) {
        caseProfileRepository.findByUploaderIdAndCaseKey(user.getUserId(), caseKey)
                .ifPresent(profile -> {
                    profile.updateRepresentativeEvidence(null);
                    caseProfileRepository.save(profile);
                });
    }

    private List<Evidence> loadCaseEvidences(User user, String caseKey) {
        return evidenceRepository.findByUploaderIdAndCaseKey(user.getUserId(), caseKey);
    }

    private long countActiveEvidences(List<Evidence> caseEvidences) {
        return caseEvidences.stream().filter(Evidence::isWorkflowActive).count();
    }

    private String normalizeReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason.trim();
    }

    private void assignDisplayLabelIfMissing(Evidence evidence, List<Evidence> caseEvidences) {
        if (evidence.getDisplayLabel() != null && !evidence.getDisplayLabel().isBlank()) {
            return;
        }
        int nextIndex = (int) caseEvidences.stream()
                .filter(item -> item.getLifecycleStatus() != EvidenceLifecycleStatus.REPLACED)
                .count() + 1;
        evidence.assignDisplayLabel("증거 " + nextIndex);
    }

    private void migrateCaseProfile(User user, String oldKey, String newKey) {
        caseProfileRepository.findByUploaderIdAndCaseKey(user.getUserId(), oldKey)
                .ifPresent(profile -> {
                    profile.updateCaseKey(newKey);
                    caseProfileRepository.save(profile);
                });
    }

    private void assertNoDuplicateCase(Long uploaderId, String caseKey) {
        assertNoDuplicateCase(uploaderId, caseKey, null);
    }

    private void assertNoDuplicateCase(Long uploaderId, String caseKey, String ignoredCaseKey) {
        if (caseKey.equalsIgnoreCase(ignoredCaseKey)) {
            return;
        }
        if (caseProfileRepository.existsByUploaderIdAndCaseKey(uploaderId, caseKey)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "DUPLICATE_CASE_NAME", "이미 사용 중인 사건명입니다.");
        }
        if (!evidenceRepository.findByUploaderIdAndCaseKey(uploaderId, caseKey).isEmpty()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "DUPLICATE_CASE_NAME", "이미 사용 중인 사건명입니다.");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
