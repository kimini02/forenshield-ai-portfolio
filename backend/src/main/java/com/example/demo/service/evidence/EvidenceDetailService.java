package com.example.demo.service.evidence;

import com.example.demo.service.evidence.hls.EvidenceHlsLookupService;
import com.example.demo.service.evidence.hls.EvidenceHlsPlaybackService;
import com.example.demo.service.custody.RecoveryScoreService;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.dto.detail.RecoveryScoreDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.CaseKeyNormalizer;
import com.example.demo.util.UserRoleSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceDetailService {

    private final EvidenceRepository evidenceRepository;
    private final CaseProfileRepository caseProfileRepository;
    private final UserRepository userRepository;
    private final EvidenceAccessService evidenceAccessService;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final CustodyLogRepository custodyLogRepository;
    private final EvidenceManifestService evidenceManifestService;
    private final RecoveryScoreService recoveryScoreService;
    private final CaseDetailAssembler caseDetailAssembler;
    private final EvidenceDetailAssembler evidenceDetailAssembler;
    private final EvidenceHlsLookupService evidenceHlsLookupService;
    private final EvidenceHlsPlaybackService evidenceHlsPlaybackService;

    public EvidenceDetailResponse getEvidenceDetail(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        return buildEvidenceDetail(user, evidence, null);
    }

    /**
     * RQ-SEC-153: 상세 조회 시 이미 수행한 무결성 검증 결과를 재사용해 중복 검증을 방지한다.
     */
    public EvidenceDetailResponse getEvidenceDetail(User user, Evidence evidence, IntegrityVerifyResponse verification) {
        return buildEvidenceDetail(user, evidence, verification);
    }

    private EvidenceDetailResponse buildEvidenceDetail(
            User user,
            Evidence evidence,
            IntegrityVerifyResponse verification
    ) {
        Long evidenceId = evidence.getEvidenceId();
        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElse(null);
        AnalysisResult result = request == null
                ? null
                : analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId()).orElse(null);
        List<AnalysisModuleResult> moduleResults = result == null
                ? List.of()
                : analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(
                        result.getAnalysisResultId()
                );
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        List<CustodyLog> custodyLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);
        EvidenceManifest manifest = evidenceManifestService.findByEvidenceId(evidenceId).orElse(null);
        RecoveryScoreDto recovery = recoveryScoreService.calculate(metadata);
        var hlsPlayback = evidenceHlsPlaybackService.buildForDetail(user.getUserId(), evidence);

        return evidenceDetailAssembler.assemble(
                evidence,
                verification,
                metadata,
                request,
                result,
                moduleResults,
                custodyLogs,
                manifest,
                recovery,
                hlsPlayback
        );
    }

    public CaseDetailResponse getCaseDetail(User user, String caseKey, String pathCaseId) {
        return getCaseDetailInternal(user, CaseKeyNormalizer.resolveCaseKey(caseKey, pathCaseId), null);
    }

    public CaseDetailResponse getCaseDetail(User user, String caseKey, String pathCaseId, Long uploaderId) {
        return getCaseDetailInternal(user, CaseKeyNormalizer.resolveCaseKey(caseKey, pathCaseId), uploaderId);
    }

    public CaseDetailResponse getCaseDetail(User user, String caseId) {
        return getCaseDetailInternal(user, CaseKeyNormalizer.requireCaseKey(caseId), null);
    }

    public CaseDetailResponse getCaseDetail(User user, String caseId, Long uploaderId) {
        return getCaseDetailInternal(user, CaseKeyNormalizer.requireCaseKey(caseId), uploaderId);
    }

    private CaseDetailResponse getCaseDetailInternal(User user, String normalizedCaseId, Long uploaderId) {
        ResolvedCaseAccess access = resolveCaseAccess(user, normalizedCaseId, uploaderId);
        if (access.evidences().isEmpty()) {
            if (access.profile() == null) {
                throw caseNotFound();
            }
            return caseDetailAssembler.assembleEmptyCase(
                    normalizedCaseId,
                    access.profile(),
                    access.uploader()
            );
        }

        List<AnalysisRequest> requests = analysisRequestRepository.findByEvidenceIdInOrderByRequestedAtDesc(
                access.evidences().stream().map(Evidence::getEvidenceId).toList()
        );
        var hlsByEvidenceId = evidenceHlsLookupService.findByEvidenceIds(
                access.evidences().stream().map(Evidence::getEvidenceId).toList()
        );
        return caseDetailAssembler.assemble(
                access.ownerUser(),
                normalizedCaseId,
                access.evidences(),
                requests,
                access.profile(),
                access.uploader(),
                hlsByEvidenceId
        );
    }

    private ResolvedCaseAccess resolveCaseAccess(User user, String normalizedCaseId, Long uploaderId) {
        if (UserRoleSupport.isInvestigator(user.getRole())) {
            return resolveInvestigatorCaseAccess(user, normalizedCaseId);
        }
        if (UserRoleSupport.isReviewer(user.getRole())) {
            return resolveReviewerCaseAccess(user, normalizedCaseId);
        }
        if (UserRoleSupport.isOrgAdmin(user.getRole())) {
            if (user.getRole() == UserRole.ROLE_ADMIN) {
                return resolveSuperAdminCaseAccess(normalizedCaseId, uploaderId);
            }
            return resolveOrgAdminCaseAccess(user, normalizedCaseId, uploaderId);
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "사건 조회 권한이 없습니다.");
    }

    private ResolvedCaseAccess resolveInvestigatorCaseAccess(User user, String normalizedCaseId) {
        Long ownerId = user.getUserId();
        Optional<CaseProfile> profile = caseProfileRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        if (profile.isEmpty() && evidences.isEmpty()) {
            throw caseNotFound();
        }
        return new ResolvedCaseAccess(user, user, profile.orElse(null), evidences);
    }

    private ResolvedCaseAccess resolveReviewerCaseAccess(User user, String normalizedCaseId) {
        CaseProfile profile = caseProfileRepository.findByReviewerId(user.getUserId()).stream()
                .filter(item -> normalizedCaseId.equalsIgnoreCase(item.getCaseKey()))
                .findFirst()
                .orElseThrow(this::caseNotFound);
        return loadCaseAccessForOwner(profile.getUploaderId(), normalizedCaseId, profile);
    }

    private ResolvedCaseAccess resolveOrgAdminCaseAccess(User admin, String normalizedCaseId, Long uploaderId) {
        Long ownerId = resolveOwnerIdInOrganization(
                userRepository.findUserIdsByOrganizationType(admin.getOrganizationType()),
                normalizedCaseId,
                uploaderId
        );
        User owner = requireActiveUser(ownerId);
        if (!Objects.equals(owner.getOrganizationType(), admin.getOrganizationType())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "사건 조회 권한이 없습니다.");
        }
        Optional<CaseProfile> profile = caseProfileRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        if (profile.isEmpty() && evidences.isEmpty()) {
            throw caseNotFound();
        }
        return new ResolvedCaseAccess(owner, owner, profile.orElse(null), evidences);
    }

    private ResolvedCaseAccess resolveSuperAdminCaseAccess(String normalizedCaseId, Long uploaderId) {
        if (uploaderId != null) {
            return loadCaseAccessForOwner(uploaderId, normalizedCaseId, null);
        }
        Set<Long> ownerIds = new HashSet<>();
        for (CaseProfile profile : caseProfileRepository.findByCaseKey(normalizedCaseId)) {
            ownerIds.add(profile.getUploaderId());
        }
        List<Evidence> allEvidences = evidenceRepository.findByCaseKey(normalizedCaseId);
        for (Evidence evidence : allEvidences) {
            ownerIds.add(evidence.getUploaderId());
        }
        if (ownerIds.isEmpty()) {
            throw caseNotFound();
        }
        if (ownerIds.size() > 1) {
            throw ambiguousOwner();
        }
        Long ownerId = ownerIds.iterator().next();
        Optional<CaseProfile> profile = caseProfileRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        User owner = requireActiveUser(ownerId);
        return new ResolvedCaseAccess(owner, owner, profile.orElse(null), evidences);
    }

    private ResolvedCaseAccess loadCaseAccessForOwner(
            Long ownerId,
            String normalizedCaseId,
            CaseProfile knownProfile
    ) {
        User owner = requireActiveUser(ownerId);
        Optional<CaseProfile> profile = knownProfile != null
                ? Optional.of(knownProfile)
                : caseProfileRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(ownerId, normalizedCaseId);
        if (profile.isEmpty() && evidences.isEmpty()) {
            throw caseNotFound();
        }
        return new ResolvedCaseAccess(owner, owner, profile.orElse(null), evidences);
    }

    private Long resolveOwnerIdInOrganization(
            List<Long> orgUploaderIds,
            String normalizedCaseId,
            Long requestedUploaderId
    ) {
        if (requestedUploaderId != null) {
            if (!orgUploaderIds.contains(requestedUploaderId)) {
                throw caseNotFound();
            }
            return requestedUploaderId;
        }

        Set<Long> ownerIds = new HashSet<>();
        for (Long orgUploaderId : orgUploaderIds) {
            if (caseProfileRepository.findByUploaderIdAndCaseKey(orgUploaderId, normalizedCaseId).isPresent()) {
                ownerIds.add(orgUploaderId);
            }
            if (!evidenceRepository.findByUploaderIdAndCaseKey(orgUploaderId, normalizedCaseId).isEmpty()) {
                ownerIds.add(orgUploaderId);
            }
        }
        if (ownerIds.isEmpty()) {
            throw caseNotFound();
        }
        if (ownerIds.size() > 1) {
            throw ambiguousOwner();
        }
        return ownerIds.iterator().next();
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(this::caseNotFound);
    }

    private BusinessException caseNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다.");
    }

    private BusinessException ambiguousOwner() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "UPLOADER_ID_REQUIRED",
                "동일 사건명이 여러 건 존재합니다. uploaderId를 지정해 주세요."
        );
    }

    private record ResolvedCaseAccess(User ownerUser, User uploader, CaseProfile profile, List<Evidence> evidences) {
    }
}
