package com.example.demo.service.user;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.dto.mypage.CaseSummaryResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CaseListQueryRepository;
import com.example.demo.repository.CaseListQueryRepository.CaseListKey;
import com.example.demo.repository.CaseListQueryRepository.CaseListPage;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.evidence.CaseEvidencePresentationService;
import com.example.demo.util.AiResultMapper;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.OrganizationIdResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

	private final AnalysisRequestRepository analysisRequestRepository;
	private final AnalysisResultRepository analysisResultRepository;
	private final CaseEvidencePresentationService caseEvidencePresentationService;
	private final UserRepository userRepository;
	private final CaseListQueryRepository caseListQueryRepository;

	public AnalysisHistoryPageResponse getAnalysisHistory(
			User user,
			String sort,
			int page,
			int size,
			String status,
			String q
	) {
		int safeSize = Math.max(size, 1);
		String normalizedStatus = normalizeStatusFilter(status);
		String keyword = normalizeKeyword(q);
		CaseListPage keyPage = caseListQueryRepository.findPage(
				user,
				sort,
				page,
				safeSize,
				normalizedStatus,
				keyword
		);
		List<CaseListKey> pageKeys = keyPage.content();
		List<Evidence> evidences = caseListQueryRepository.findEvidencesByCaseKeys(pageKeys);
		Map<CaseListKey, List<Evidence>> evidenceByCase = groupEvidencesByCase(evidences);
		Map<CaseListKey, CaseProfile> profileByCase = loadProfilesByCase(pageKeys);
		Map<Long, User> uploaderById = loadUploaders(pageKeys);

		List<Long> evidenceIds = evidences.stream().map(Evidence::getEvidenceId).toList();
		Map<Long, AnalysisRequest> latestRequestByEvidence = evidenceIds.isEmpty()
				? Map.of()
				: loadLatestRequests(evidenceIds);
		Map<Long, AnalysisResult> resultByRequestId = loadResults(latestRequestByEvidence.values());

		List<CaseSummaryResponse> pageContent = new ArrayList<>(pageKeys.size());
		for (CaseListKey key : pageKeys) {
			List<Evidence> caseEvidences = evidenceByCase.getOrDefault(key, List.of());
			CaseProfile profile = profileByCase.get(key);
			if (caseEvidences.isEmpty()) {
				if (profile != null) {
					pageContent.add(toProfileOnlyCaseSummary(profile, uploaderById.get(key.uploaderId())));
				}
				continue;
			}
			pageContent.add(toCaseSummary(
					key.caseKey(),
					caseEvidences,
					latestRequestByEvidence,
					resultByRequestId,
					profile == null ? null : profile.getRepresentativeEvidenceId(),
					uploaderById,
					profile
			));
		}

		int totalPages = keyPage.totalElements() == 0
				? 0
				: (int) Math.ceil((double) keyPage.totalElements() / safeSize);

		return AnalysisHistoryPageResponse.builder()
				.content(pageContent)
				.page(page)
				.size(safeSize)
				.totalElements(keyPage.totalElements())
				.totalPages(totalPages)
				.build();
	}

	private Map<CaseListKey, List<Evidence>> groupEvidencesByCase(List<Evidence> evidences) {
		Map<CaseListKey, List<Evidence>> grouped = new HashMap<>();
		for (Evidence evidence : evidences) {
			CaseListKey key = new CaseListKey(
					evidence.getUploaderId(),
					caseEvidencePresentationService.resolveCaseKey(evidence)
			);
			grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(evidence);
		}
		return grouped;
	}

	private Map<CaseListKey, CaseProfile> loadProfilesByCase(List<CaseListKey> pageKeys) {
		Map<CaseListKey, CaseProfile> profiles = new HashMap<>();
		for (CaseProfile profile : caseListQueryRepository.findProfilesByCaseKeys(pageKeys)) {
			profiles.put(new CaseListKey(profile.getUploaderId(), profile.getCaseKey()), profile);
		}
		return profiles;
	}

	private Map<Long, User> loadUploaders(List<CaseListKey> pageKeys) {
		List<Long> uploaderIds = pageKeys.stream().map(CaseListKey::uploaderId).distinct().toList();
		Map<Long, User> uploaders = new HashMap<>();
		for (User uploader : userRepository.findAllById(uploaderIds)) {
			uploaders.put(uploader.getUserId(), uploader);
		}
		return uploaders;
	}

	private String normalizeStatusFilter(String status) {
		if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
			return null;
		}
		return status.trim().toUpperCase();
	}

	private String normalizeKeyword(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		return q.trim().toLowerCase(Locale.ROOT);
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
				.findByEvidenceIdInOrderByRequestedAtDescAnalysisRequestIdDesc(evidenceIds);

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

}
