package com.example.demo.service.evidence;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.EvidenceCaseIdResolver;
import com.example.demo.util.UserRoleSupport;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EvidenceAccessService {

    private final EvidenceRepository evidenceRepository;
    private final CaseAccessService caseAccessService;

    public Evidence requireOwned(User user, Long evidenceId) {
        return requireOwned(user.getUserId(), evidenceId);
    }

    public Evidence requireOwned(Long userId, Long evidenceId) {
        return evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, userId)
                .orElseThrow(this::evidenceNotFound);
    }

    /**
     * Read-only access for evidence detail, status, integrity, reports, etc.
     * Allows owner, assigned reviewer, and org admin for cases in their organization.
     */
    public Evidence requireReadable(User user, Long evidenceId) {
        Optional<Evidence> owned = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId());
        if (owned.isPresent()) {
            return owned.get();
        }
        return loadSharedReadableEvidence(user, evidenceId).orElseThrow(this::evidenceNotFound);
    }

    private Optional<Evidence> loadSharedReadableEvidence(User user, Long evidenceId) {
        if (!UserRoleSupport.isReviewer(user.getRole()) && !UserRoleSupport.isOrgAdmin(user.getRole())) {
            return Optional.empty();
        }

        Optional<Evidence> evidence = evidenceRepository.findByEvidenceIdAndDeletedAtIsNull(evidenceId);
        if (evidence.isEmpty()) {
            return Optional.empty();
        }

        String caseKey = EvidenceCaseIdResolver.resolve(evidence.get());
        try {
            CaseAccessService.CaseAccessContext context = caseAccessService.requireAccessibleCase(user, caseKey);
            boolean belongsToCase = context.evidences().stream()
                    .anyMatch(caseEvidence -> caseEvidence.getEvidenceId().equals(evidenceId));
            return belongsToCase ? evidence : Optional.empty();
        } catch (BusinessException ex) {
            if ("CASE_NOT_FOUND".equals(ex.getErrorCode()) || "FORBIDDEN".equals(ex.getErrorCode())) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private BusinessException evidenceNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다.");
    }
}
