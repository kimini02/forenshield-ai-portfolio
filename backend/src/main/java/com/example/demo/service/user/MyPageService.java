package com.example.demo.service.user;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.dto.mypage.CaseSummaryResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.evidence.CaseEvidencePresentationService;
import com.example.demo.util.AiResultMapper;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.OrganizationIdResolver;
import com.example.demo.util.UserRoleSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

	private static final Map<String, Integer> STATUS_ORDER = Map.of(
			"PROCESSING", 0,
			"PENDING", 1,
			"FAILED", 2,
			"COMPLETED", 3
	);

	private final EvidenceRepository evidenceRepository;
	private final CaseProfileRepository caseProfileRepository;
	private final AnalysisRequestRepository analysisRequestRepository;
	private final AnalysisResultRepository analysisResultRepository;
	private final CaseEvidencePresentationService caseEvidencePresentationService;
	private final UserRepository userRepository;

	public AnalysisHistoryPageResponse getAnalysisHistory(
			User user,
			String sort,
			int page,
			int size,
			String status,
			String q
	) {
		if (UserRoleSupport.isOrgAdmin(user.getRole()) || UserRoleSupport.isReviewer(user.getRole())) {
			return getAnalysisHistoryForPrivilegedUser(user, sort, page, size, status, q);
		}
		return getAnalysisHistoryForInvestigator(user, sort, page, size, status, q);
	}

	/** develop baseline: uploader scope + empty profile-only cases. */
	private AnalysisHistoryPageResponse getAnalysisHistoryForInvestigator(
			User user,
			String sort,
			int page,
			int size,
			String status,
			String q
	) {
		List<Evidence> evidences = evidenceRepository
				.findByUploaderIdAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
						user.getUserId(), EvidenceStatus.UPLOADED);

		Map<Long, User> uploaderById = Map.of(user.getUserId(), user);
		Map<String, CaseProfile> profileByCaseKey = loadProfilesByCaseKey(user.getUserId());

		return buildPageResponse(user, sort, page, size, status, q, evidences, uploaderById, profileByCaseKey, false);
	}

	private AnalysisHistoryPageResponse getAnalysisHistoryForPrivilegedUser(
			User user,
			String sort,
			int page,
			int size,
			String status,
			String q
	) {
		List<Evidence> evidences = loadVisibleEvidences(user);
		Map<Long, User> uploaderById = loadUploaders(evidences);
		Map<String, CaseProfile> profileByCaseOwnerKey = loadProfilesByCaseOwnerKey(evidences);

		return buildPageResponse(user, sort, page, size, status, q, evidences, uploaderById, profileByCaseOwnerKey, true);
	}

	private AnalysisHistoryPageResponse buildPageResponse(
			User user,
			String sort,
			int page,
			int size,
			String status,
			String q,
			List<Evidence> evidences,
			Map<Long, User> uploaderById,
			Map<String, CaseProfile> profileLookup,
			boolean privilegedScope
	) {
		List<Long> evidenceIds = evidences.stream().map(Evidence::getEvidenceId).toList();
		Map<Long, AnalysisRequest> latestRequestByEvidence = evidenceIds.isEmpty()
				? Map.of()
				: loadLatestRequests(evidenceIds);
		Map<Long, AnalysisResult> resultByRequestId = loadResults(latestRequestByEvidence.values());
		Map<String, List<Evidence>> groupedByCase = caseEvidencePresentationService.groupByCaseKey(evidences);
		Map<String, Long> representativeByCase = loadRepresentativeEvidenceIds(
				user,
				groupedByCase,
				profileLookup,
				privilegedScope
		);

		List<CaseSummaryResponse> caseSummaries = new ArrayList<>(groupedByCase.entrySet().stream()
				.map(entry -> toCaseSummary(
						entry.getKey(),
						entry.getValue(),
						latestRequestByEvidence,
						resultByRequestId,
						representativeByCase.get(entry.getKey()),
						uploaderById,
						resolveProfile(entry.getKey(), entry.getValue(), profileLookup, privilegedScope)
				))
				.filter(summary -> !privilegedScope || isVisibleCaseForUser(user, summary))
				.toList());

		Set<String> caseKeysWithEvidence = new HashSet<>(groupedByCase.keySet());
		appendProfileOnlyCases(user, caseSummaries, caseKeysWithEvidence, uploaderById, privilegedScope);

		caseSummaries.sort(buildCaseComparator(sort));
		caseSummaries = applyCaseFilters(caseSummaries, status, q);

		int safeSize = Math.max(size, 1);
		int fromIndex = Math.min(page * safeSize, caseSummaries.size());
		int toIndex = Math.min(fromIndex + safeSize, caseSummaries.size());
		List<CaseSummaryResponse> pageContent = caseSummaries.subList(fromIndex, toIndex);
		int totalPages = caseSummaries.isEmpty() ? 0 : (int) Math.ceil((double) caseSummaries.size() / safeSize);

		return AnalysisHistoryPageResponse.builder()
				.content(pageContent)
				.page(page)
				.size(safeSize)
				.totalElements(caseSummaries.size())
				.totalPages(totalPages)
				.build();
	}

	private List<CaseSummaryResponse> applyCaseFilters(
			List<CaseSummaryResponse> caseSummaries,
			String status,
			String q
	) {
		String normalizedStatus = normalizeStatusFilter(status);
		String keyword = q == null ? "" : q.trim().toLowerCase();

		return caseSummaries.stream()
				.filter(summary -> matchesStatusFilter(summary, normalizedStatus))
				.filter(summary -> matchesKeywordFilter(summary, keyword))
				.toList();
	}

	private String normalizeStatusFilter(String status) {
		if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
			return null;
		}
		return status.trim().toUpperCase();
	}

	private boolean matchesStatusFilter(CaseSummaryResponse summary, String normalizedStatus) {
		if (normalizedStatus == null) {
			return true;
		}
		return normalizedStatus.equalsIgnoreCase(summary.getStatus());
	}

	private boolean matchesKeywordFilter(CaseSummaryResponse summary, String keyword) {
		if (keyword.isEmpty()) {
			return true;
		}
		String caseName = summary.getCaseName() == null ? "" : summary.getCaseName().toLowerCase();
		String label = summary.getRepresentativeEvidenceLabel() == null
				? ""
				: summary.getRepresentativeEvidenceLabel().toLowerCase();
		String evidenceId = summary.getRepresentativeEvidenceId() == null
				? ""
				: ("evd-" + summary.getRepresentativeEvidenceId()).toLowerCase();
		return caseName.contains(keyword) || label.contains(keyword) || evidenceId.contains(keyword);
	}

	private List<Evidence> loadVisibleEvidences(User user) {
		if (UserRoleSupport.isOrgAdmin(user.getRole())) {
			if (isGlobalAdmin(user)) {
				return evidenceRepository.findByStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
						EvidenceStatus.UPLOADED
				);
			}
			List<Long> orgUploaderIds = userRepository.findUserIdsByOrganizationType(user.getOrganizationType());
			if (orgUploaderIds.isEmpty()) {
				return List.of();
			}
			return evidenceRepository.findByUploaderIdInAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
					orgUploaderIds,
					EvidenceStatus.UPLOADED
			);
		}
		return evidenceRepository.findByReviewerAssignmentAndStatus(user.getUserId(), EvidenceStatus.UPLOADED);
	}

	private void appendProfileOnlyCases(
			User user,
			List<CaseSummaryResponse> caseSummaries,
			Set<String> caseKeysWithEvidence,
			Map<Long, User> uploaderById,
			boolean privilegedScope
	) {
		List<CaseProfile> profiles = loadVisibleProfiles(user);
		if (profiles.isEmpty()) {
			return;
		}

		List<Long> missingUploaderIds = profiles.stream()
				.map(CaseProfile::getUploaderId)
				.filter(uploaderId -> !uploaderById.containsKey(uploaderId))
				.distinct()
				.toList();
		for (User uploader : userRepository.findAllById(missingUploaderIds)) {
			uploaderById.put(uploader.getUserId(), uploader);
		}

		for (CaseProfile profile : profiles) {
			if (caseKeysWithEvidence.contains(profile.getCaseKey())) {
				continue;
			}
			CaseSummaryResponse summary = toProfileOnlyCaseSummary(
					profile,
					uploaderById.get(profile.getUploaderId())
			);
			if (!privilegedScope || isVisibleCaseForUser(user, summary)) {
				caseSummaries.add(summary);
			}
		}
	}

	private List<CaseProfile> loadVisibleProfiles(User user) {
		if (UserRoleSupport.isOrgAdmin(user.getRole())) {
			if (isGlobalAdmin(user)) {
				return caseProfileRepository.findAll();
			}
			List<Long> orgUploaderIds = userRepository.findUserIdsByOrganizationType(user.getOrganizationType());
			if (orgUploaderIds.isEmpty()) {
				return List.of();
			}
			return caseProfileRepository.findByUploaderIdIn(orgUploaderIds);
		}
		if (UserRoleSupport.isReviewer(user.getRole())) {
			return caseProfileRepository.findByReviewerId(user.getUserId());
		}
		return caseProfileRepository.findByUploaderId(user.getUserId());
	}

	private boolean isVisibleCaseForUser(User user, CaseSummaryResponse summary) {
		if (UserRoleSupport.isOrgAdmin(user.getRole())) {
			if (isGlobalAdmin(user)) {
				return true;
			}
			return Objects.equals(summary.getOrganizationId(), OrganizationIdResolver.resolve(user.getOrganizationType()));
		}
		if (UserRoleSupport.isReviewer(user.getRole())) {
			return String.valueOf(user.getUserId()).equals(summary.getReviewerId());
		}
		return true;
	}

	private boolean isGlobalAdmin(User user) {
		return user.getRole() == UserRole.ROLE_ADMIN || user.getOrganizationType() == null;
	}

	private Map<Long, User> loadUploaders(List<Evidence> evidences) {
		List<Long> uploaderIds = evidences.stream()
				.map(Evidence::getUploaderId)
				.distinct()
				.toList();
		Map<Long, User> uploaders = new HashMap<>();
		for (User uploader : userRepository.findAllById(uploaderIds)) {
			uploaders.put(uploader.getUserId(), uploader);
		}
		return uploaders;
	}

	private Map<String, CaseProfile> loadProfilesByCaseKey(Long uploaderId) {
		Map<String, CaseProfile> profiles = new HashMap<>();
		for (CaseProfile profile : caseProfileRepository.findByUploaderId(uploaderId)) {
			profiles.put(profile.getCaseKey(), profile);
		}
		return profiles;
	}

	private Map<String, CaseProfile> loadProfilesByCaseOwnerKey(List<Evidence> evidences) {
		List<Long> uploaderIds = evidences.stream().map(Evidence::getUploaderId).distinct().toList();
		if (uploaderIds.isEmpty()) {
			return Map.of();
		}
		Map<String, CaseProfile> profiles = new HashMap<>();
		for (CaseProfile profile : caseProfileRepository.findByUploaderIdIn(uploaderIds)) {
			profiles.put(caseOwnerKey(profile.getUploaderId(), profile.getCaseKey()), profile);
		}
		return profiles;
	}

	private Map<String, Long> loadRepresentativeEvidenceIds(
			User user,
			Map<String, List<Evidence>> groupedByCase,
			Map<String, CaseProfile> profileLookup,
			boolean privilegedScope
	) {
		if (privilegedScope) {
			Map<String, Long> representatives = new HashMap<>();
			for (Map.Entry<String, List<Evidence>> entry : groupedByCase.entrySet()) {
				Evidence first = entry.getValue().get(0);
				CaseProfile profile = profileLookup.get(caseOwnerKey(first.getUploaderId(), entry.getKey()));
				if (profile != null && profile.getRepresentativeEvidenceId() != null) {
					representatives.put(entry.getKey(), profile.getRepresentativeEvidenceId());
				}
			}
			return representatives;
		}
		return caseEvidencePresentationService.loadRepresentativeEvidenceIds(
				user,
				new ArrayList<>(groupedByCase.keySet())
		);
	}

	private CaseProfile resolveProfile(
			String caseId,
			List<Evidence> caseEvidences,
			Map<String, CaseProfile> profileLookup,
			boolean privilegedScope
	) {
		if (profileLookup.isEmpty()) {
			return null;
		}
		if (privilegedScope) {
			Evidence first = caseEvidences.get(0);
			return profileLookup.get(caseOwnerKey(first.getUploaderId(), caseId));
		}
		return profileLookup.get(caseId);
	}

	private CaseSummaryResponse toCaseSummary(
			String caseId,
			List<Evidence> caseEvidences,
			Map<Long, AnalysisRequest> latestRequestByEvidence,
			Map<Long, AnalysisResult> resultByRequestId,
			Long representativeEvidenceId,
			Map<Long, User> uploaderById,
			CaseProfile profile
	) {
		List<Evidence> ordered = caseEvidencePresentationService.orderForDisplay(caseEvidences);
		Evidence representativeEvidence = caseEvidencePresentationService.findRepresentativeEvidence(
				ordered,
				representativeEvidenceId
		);
		if (representativeEvidence == null) {
			representativeEvidence = ordered.stream()
					.filter(Evidence::isWorkflowActive)
					.findFirst()
					.orElse(ordered.get(0));
		}

		String aggregateStatus = ordered.stream()
				.map(evidence -> resolveEvidenceStatus(evidence, latestRequestByEvidence))
				.reduce(this::higherPriorityStatus)
				.orElse("PENDING");

		String createdAt = ordered.stream()
				.map(evidence -> ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
				.min(String::compareTo)
				.orElse(ApiDateTimeFormatter.formatUtc(representativeEvidence.getUploadedAt()));

		Double maxRiskScore = ordered.stream()
				.map(evidence -> latestRequestByEvidence.get(evidence.getEvidenceId()))
				.filter(Objects::nonNull)
				.map(request -> resultByRequestId.get(request.getAnalysisRequestId()))
				.filter(result -> result != null && result.getRiskScore() != null)
				.map(AnalysisResult::getRiskScore)
				.max(Double::compareTo)
				.orElse(null);

		String caseName = representativeEvidence.getCaseName() != null && !representativeEvidence.getCaseName().isBlank()
				? representativeEvidence.getCaseName()
				: caseId;

		User uploader = uploaderById.get(representativeEvidence.getUploaderId());
		Long ownerId = representativeEvidence.getUploaderId();
		Long assigneeId = profile != null && profile.getAssigneeId() != null ? profile.getAssigneeId() : ownerId;
		String reviewStatus = profile != null && profile.getReviewStatus() != null
				? profile.getReviewStatus().name()
				: "NONE";

		return CaseSummaryResponse.builder()
				.caseId(caseId)
				.caseName(caseName)
				.status(aggregateStatus)
				.createdAt(createdAt)
				.evidenceCount(ordered.size())
				.representativeFileName(representativeEvidence.getFileName())
				.representativeEvidenceId(representativeEvidence.getEvidenceId())
				.representativeEvidenceLabel(
						caseEvidencePresentationService.resolveDisplayLabel(representativeEvidence, ordered)
				)
				.riskScore(maxRiskScore)
				.organizationId(uploader == null
						? null
						: OrganizationIdResolver.resolve(uploader.getOrganizationType()))
				.department(uploader == null ? null : uploader.getDepartment())
				.createdBy(String.valueOf(ownerId))
				.assigneeId(String.valueOf(assigneeId))
				.reviewerId(profile != null && profile.getReviewerId() != null
						? String.valueOf(profile.getReviewerId())
						: null)
				.reviewStatus(reviewStatus)
				.aiResult(AiResultMapper.fromRiskScore(maxRiskScore))
				.reviewRequestedAt(profile != null && profile.getReviewRequestedAt() != null
						? ApiDateTimeFormatter.formatUtc(profile.getReviewRequestedAt())
						: null)
				.build();
	}

	private CaseSummaryResponse toProfileOnlyCaseSummary(CaseProfile profile, User uploader) {
		Long assigneeId = profile.getAssigneeId() != null ? profile.getAssigneeId() : profile.getUploaderId();
		return CaseSummaryResponse.builder()
				.caseId(profile.getCaseKey())
				.caseName(profile.getCaseKey())
				.status("PENDING")
				.createdAt(ApiDateTimeFormatter.formatUtc(profile.getUpdatedAt()))
				.evidenceCount(0)
				.organizationId(uploader == null
						? null
						: OrganizationIdResolver.resolve(uploader.getOrganizationType()))
				.department(uploader == null ? null : uploader.getDepartment())
				.createdBy(String.valueOf(profile.getUploaderId()))
				.assigneeId(String.valueOf(assigneeId))
				.reviewerId(profile.getReviewerId() != null ? String.valueOf(profile.getReviewerId()) : null)
				.reviewStatus(profile.getReviewStatus().name())
				.build();
	}

	private String caseOwnerKey(Long uploaderId, String caseKey) {
		return uploaderId + "::" + caseKey;
	}

	private String resolveEvidenceStatus(Evidence evidence, Map<Long, AnalysisRequest> latestRequestByEvidence) {
		AnalysisRequest request = latestRequestByEvidence.get(evidence.getEvidenceId());
		if (request == null) {
			return "PENDING";
		}
		return AnalysisStatusMapper.toApiStatus(request.getStatus());
	}

	private String higherPriorityStatus(String current, String candidate) {
		return STATUS_ORDER.get(candidate) < STATUS_ORDER.get(current) ? candidate : current;
	}

	private Map<Long, AnalysisRequest> loadLatestRequests(List<Long> evidenceIds) {
		List<AnalysisRequest> requests = analysisRequestRepository
				.findByEvidenceIdInOrderByRequestedAtDesc(evidenceIds);

		Map<Long, AnalysisRequest> latest = new HashMap<>();
		for (AnalysisRequest request : requests) {
			latest.putIfAbsent(request.getEvidenceId(), request);
		}
		return latest;
	}

	private Map<Long, AnalysisResult> loadResults(Iterable<AnalysisRequest> requests) {
		List<Long> requestIds = new ArrayList<>();
		for (AnalysisRequest request : requests) {
			requestIds.add(request.getAnalysisRequestId());
		}
		if (requestIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, AnalysisResult> resultByRequestId = new HashMap<>();
		for (AnalysisResult result : analysisResultRepository.findByAnalysisRequestIdIn(requestIds)) {
			resultByRequestId.put(result.getAnalysisRequestId(), result);
		}
		return resultByRequestId;
	}

	private Comparator<CaseSummaryResponse> buildCaseComparator(String sort) {
		if ("status".equalsIgnoreCase(sort)) {
			return Comparator
					.comparing((CaseSummaryResponse item) -> STATUS_ORDER.getOrDefault(item.getStatus(), 99))
					.thenComparing(CaseSummaryResponse::getCreatedAt, Comparator.reverseOrder());
		}
		if ("oldest".equalsIgnoreCase(sort)) {
			return Comparator.comparing(CaseSummaryResponse::getCreatedAt);
		}

		return Comparator.comparing(CaseSummaryResponse::getCreatedAt, Comparator.reverseOrder());
	}
}
