package com.example.demo.service.evidence;

import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.EvidenceLifecycleStatus;
import com.example.demo.domain.enums.EvidenceRole;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CaseEvidencePresentationService {

    private final CaseProfileRepository caseProfileRepository;

    public String resolveDisplayLabel(Evidence evidence, List<Evidence> caseEvidences) {
        if (evidence.getDisplayLabel() != null && !evidence.getDisplayLabel().isBlank()) {
            return evidence.getDisplayLabel();
        }
        List<Evidence> ordered = orderForDisplay(caseEvidences);
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).getEvidenceId().equals(evidence.getEvidenceId())) {
                return "증거 " + (index + 1);
            }
        }
        return "증거";
    }

    public List<Evidence> orderForDisplay(List<Evidence> caseEvidences) {
        return caseEvidences.stream()
                .sorted(Comparator.comparing(Evidence::getEvidenceId))
                .toList();
    }

    public Optional<Long> resolveRepresentativeEvidenceId(User user, String caseKey, List<Evidence> caseEvidences) {
        return resolveRepresentativeEvidenceId(user.getUserId(), caseKey, caseEvidences);
    }

    public Optional<Long> resolveRepresentativeEvidenceId(
            Long uploaderId,
            String caseKey,
            List<Evidence> caseEvidences
    ) {
        Optional<Long> profileRepresentative = caseProfileRepository
                .findByUploaderIdAndCaseKey(uploaderId, caseKey)
                .map(CaseProfile::getRepresentativeEvidenceId);

        if (profileRepresentative.isPresent()) {
            return profileRepresentative;
        }

        return caseEvidences.stream()
                .filter(evidence -> evidence.getEvidenceRole() == EvidenceRole.PRIMARY)
                .map(Evidence::getEvidenceId)
                .findFirst()
                .or(() -> caseEvidences.stream()
                        .filter(Evidence::isWorkflowActive)
                        .map(Evidence::getEvidenceId)
                        .min(Long::compareTo));
    }

    public Map<String, Long> loadRepresentativeEvidenceIds(User user, List<String> caseKeys) {
        if (caseKeys.isEmpty()) {
            return Map.of();
        }
        return caseProfileRepository.findByUploaderIdAndCaseKeyIn(user.getUserId(), caseKeys).stream()
                .filter(profile -> profile.getRepresentativeEvidenceId() != null)
                .collect(Collectors.toMap(
                        CaseProfile::getCaseKey,
                        CaseProfile::getRepresentativeEvidenceId,
                        (left, right) -> left
                ));
    }

    public String lifecycleStatusName(Evidence evidence) {
        return evidence.getLifecycleStatus() != null
                ? evidence.getLifecycleStatus().name()
                : EvidenceLifecycleStatus.ACTIVE.name();
    }

    public String roleName(Evidence evidence) {
        return evidence.getEvidenceRole() != null ? evidence.getEvidenceRole().name() : null;
    }

    public String resolveCaseKey(Evidence evidence) {
        return EvidenceCaseIdResolver.resolve(evidence);
    }

    public Map<String, List<Evidence>> groupByCaseKey(List<Evidence> evidences) {
        return evidences.stream().collect(Collectors.groupingBy(this::resolveCaseKey));
    }

    public Evidence findRepresentativeEvidence(
            List<Evidence> caseEvidences,
            Long representativeEvidenceId
    ) {
        if (representativeEvidenceId == null) {
            return null;
        }
        return caseEvidences.stream()
                .filter(evidence -> representativeEvidenceId.equals(evidence.getEvidenceId()))
                .findFirst()
                .orElse(null);
    }

    public <T> Map<Long, T> indexByEvidenceId(List<Evidence> evidences, Function<Evidence, T> mapper) {
        return evidences.stream().collect(Collectors.toMap(Evidence::getEvidenceId, mapper, (left, right) -> left));
    }
}
