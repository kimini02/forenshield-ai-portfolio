package com.example.demo.service.evidence;

import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.UserRoleSupport;
import com.example.demo.util.CaseKeyNormalizer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CaseAccessService {

    private final EvidenceRepository evidenceRepository;
    private final CaseProfileRepository caseProfileRepository;
    private final UserRepository userRepository;

    public CaseAccessContext requireAccessibleCase(User user, String caseKey) {
        String normalizedCaseKey = CaseKeyNormalizer.requireCaseKey(caseKey);
        if (UserRoleSupport.isOrgAdmin(user.getRole())) {
            return loadForOrgAdmin(user, normalizedCaseKey);
        }
        if (UserRoleSupport.isReviewer(user.getRole())) {
            return loadForReviewer(user, normalizedCaseKey);
        }
        return loadForInvestigator(user, normalizedCaseKey);
    }

    public CaseAccessContext requireOrgCase(User user, String caseKey) {
        if (!UserRoleSupport.isOrgAdmin(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "기관 관리자만 수행할 수 있습니다.");
        }
        return loadForOrgAdmin(user, CaseKeyNormalizer.requireCaseKey(caseKey));
    }

    public CaseAccessContext requireOwnedOrOrgCase(User user, String caseKey) {
        String normalizedCaseKey = CaseKeyNormalizer.requireCaseKey(caseKey);
        if (UserRoleSupport.isOrgAdmin(user.getRole())) {
            return loadForOrgAdmin(user, normalizedCaseKey);
        }
        return loadForInvestigator(user, normalizedCaseKey);
    }

    private CaseAccessContext loadForInvestigator(User user, String caseKey) {
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(user.getUserId(), caseKey);
        CaseProfile profile = caseProfileRepository
                .findByUploaderIdAndCaseKey(user.getUserId(), caseKey)
                .orElse(null);
        if (evidences.isEmpty() && profile == null) {
            throw notFound();
        }
        return new CaseAccessContext(user.getUserId(), caseKey, evidences, profile);
    }

    private CaseAccessContext loadForOrgAdmin(User user, String caseKey) {
        OrgType organizationType = user.getOrganizationType();
        if (organizationType == null) {
            throw notFound();
        }
        List<Long> uploaderIds = userRepository.findUserIdsByOrganizationType(organizationType);
        if (uploaderIds.isEmpty()) {
            throw notFound();
        }
        List<Evidence> evidences = evidenceRepository.findByCaseKeyAndUploaderIdIn(caseKey, uploaderIds);
        if (!evidences.isEmpty()) {
            Long uploaderId = evidences.get(0).getUploaderId();
            CaseProfile profile = caseProfileRepository.findByUploaderIdAndCaseKey(uploaderId, caseKey).orElse(null);
            return new CaseAccessContext(uploaderId, caseKey, evidences, profile);
        }
        List<CaseProfile> profiles = caseProfileRepository.findByCaseKeyAndUploaderIdIn(caseKey, uploaderIds);
        if (profiles.isEmpty()) {
            throw notFound();
        }
        CaseProfile profile = profiles.get(0);
        return new CaseAccessContext(profile.getUploaderId(), caseKey, List.of(), profile);
    }

    private CaseAccessContext loadForReviewer(User user, String caseKey) {
        CaseProfile profile = caseProfileRepository
                .findByReviewerIdAndCaseKey(user.getUserId(), caseKey)
                .orElseThrow(this::notFound);
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(profile.getUploaderId(), caseKey);
        return new CaseAccessContext(profile.getUploaderId(), caseKey, evidences, profile);
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다.");
    }

    public record CaseAccessContext(
            Long uploaderId,
            String caseKey,
            List<Evidence> evidences,
            CaseProfile profile
    ) {
    }
}
