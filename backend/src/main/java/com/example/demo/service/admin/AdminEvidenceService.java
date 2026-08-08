package com.example.demo.service.admin;

import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.admin.AdminEvidenceAnalysisResponse;
import com.example.demo.dto.admin.AdminEvidenceCustodyLogResponse;
import com.example.demo.dto.admin.AdminEvidenceDetailResponse;
import com.example.demo.dto.admin.AdminEvidenceItemResponse;
import com.example.demo.dto.admin.AdminEvidenceMetadataResponse;
import com.example.demo.dto.admin.AdminEvidencePageResponse;
import com.example.demo.exception.AdminException;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminEvidenceService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EvidenceRepository evidenceRepository;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final CustodyLogRepository custodyLogRepository;
    private final UserRepository userRepository;
    private final CustodyLogService custodyLogService;

    @Transactional(readOnly = true)
    public AdminEvidencePageResponse listEvidences(
            String search,
            String fileType,
            String status,
            String department,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        Specification<Evidence> specification = buildSpecification(search, fileType, status, department, from, to);
        Page<Evidence> result = evidenceRepository.findAll(specification, PageRequest.of(page, size));

        Map<Long, User> uploaders = resolveUploaders(result.getContent());
        Map<Long, AnalysisRequest> latestAnalysis = resolveLatestAnalysis(result.getContent());

        return AdminEvidencePageResponse.builder()
                .content(result.getContent().stream()
                        .map(evidence -> toItem(evidence, uploaders, latestAnalysis))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminEvidenceDetailResponse getEvidence(Long evidenceId) {
        Evidence evidence = findEvidence(evidenceId);
        User uploader = userRepository.findById(evidence.getUploaderId()).orElse(null);
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        List<AnalysisRequest> analysisHistory =
                analysisRequestRepository.findByEvidenceIdOrderByRequestedAtDesc(evidenceId);
        AnalysisRequest latestAnalysis = analysisHistory.isEmpty() ? null : analysisHistory.get(0);

        List<CustodyLog> custodyLogs = custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                CustodyTargetType.EVIDENCE,
                evidenceId
        );
        Map<Long, User> actors = resolveActors(custodyLogs);

        return AdminEvidenceDetailResponse.builder()
                .id(String.valueOf(evidence.getEvidenceId()))
                .fileName(evidence.getFileName())
                .fileType(evidence.getFileType().name())
                .mimeType(evidence.getMimeType())
                .fileSize(evidence.getFileSize())
                .caseNumber(evidence.getCaseNumber())
                .caseName(evidence.getCaseName())
                .uploaderUsername(uploader == null ? "unknown" : uploader.getLoginId())
                .uploaderName(uploader == null ? "-" : uploader.getName())
                .department(uploader == null ? "-" : uploader.getDepartment())
                .hashAlgorithm(evidence.getHashAlgorithm())
                .hashValue(evidence.getOriginalHashValue())
                .uploadedAt(formatDateTime(evidence.getUploadedAt()))
                .status(evidence.getStatus().name())
                .deletedAt(formatDateTime(evidence.getDeletedAt()))
                .analysisStatus(resolveAnalysisStatus(latestAnalysis))
                .metadata(toMetadata(metadata))
                .analysisHistory(analysisHistory.stream().map(this::toAnalysis).toList())
                .custodyLogs(custodyLogs.stream()
                        .map(log -> toCustodyLog(log, actors))
                        .toList())
                .build();
    }

    @Transactional
    public void deleteEvidence(User admin, Long evidenceId, String reason) {
        Evidence evidence = findEvidence(evidenceId);
        if (evidence.getStatus() == EvidenceStatus.DELETED) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "ALREADY_DELETED", "이미 삭제된 증거입니다.");
        }

        evidence.softDelete();
        custodyLogService.recordEvidenceAction(admin, evidence, "EVIDENCE_DELETED", reason);
    }

    private Evidence findEvidence(Long evidenceId) {
        return evidenceRepository.findByEvidenceId(evidenceId)
                .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));
    }

    private Specification<Evidence> buildSpecification(
            String search,
            String fileType,
            String status,
            String department,
            LocalDate from,
            LocalDate to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            EvidenceStatus statusFilter = parseEvidenceStatus(status);
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }

            FileType fileTypeFilter = parseFileType(fileType);
            if (fileTypeFilter != null) {
                predicates.add(cb.equal(root.get("fileType"), fileTypeFilter));
            }

            if (department != null && !department.isBlank() && !"ALL".equalsIgnoreCase(department)) {
                List<Long> uploaderIds = userRepository.findUserIdsByDepartment(department.trim());
                if (uploaderIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("uploaderId").in(uploaderIds));
                }
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("uploadedAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("uploadedAt"), to.plusDays(1).atStartOfDay()));
            }

            String normalizedSearch = search == null ? "" : search.trim();
            if (!normalizedSearch.isEmpty()) {
                String pattern = "%" + normalizedSearch.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fileName")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("caseName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("caseNumber"), "")), pattern),
                        cb.like(cb.lower(root.get("originalHashValue")), pattern)
                ));
            }

            query.orderBy(cb.desc(root.get("uploadedAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private EvidenceStatus parseEvidenceStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return EvidenceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "유효하지 않은 증거 상태입니다.");
        }
    }

    private FileType parseFileType(String fileType) {
        if (fileType == null || fileType.isBlank() || "ALL".equalsIgnoreCase(fileType)) {
            return null;
        }
        String normalized = fileType.trim().toUpperCase();
        if (!"VIDEO".equals(normalized)) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "MVP는 VIDEO만 지원합니다.");
        }
        return FileType.VIDEO;
    }

    private Map<Long, User> resolveUploaders(List<Evidence> evidences) {
        List<Long> uploaderIds = evidences.stream()
                .map(Evidence::getUploaderId)
                .distinct()
                .toList();
        if (uploaderIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(uploaderIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user));
    }

    private Map<Long, AnalysisRequest> resolveLatestAnalysis(List<Evidence> evidences) {
        List<Long> evidenceIds = evidences.stream().map(Evidence::getEvidenceId).toList();
        if (evidenceIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, AnalysisRequest> latestByEvidenceId = new HashMap<>();
        for (AnalysisRequest request : analysisRequestRepository.findByEvidenceIdInOrderByRequestedAtDesc(evidenceIds)) {
            latestByEvidenceId.putIfAbsent(request.getEvidenceId(), request);
        }
        return latestByEvidenceId;
    }

    private Map<Long, User> resolveActors(List<CustodyLog> logs) {
        List<Long> actorIds = logs.stream().map(CustodyLog::getActorId).distinct().toList();
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user));
    }

    private AdminEvidenceItemResponse toItem(
            Evidence evidence,
            Map<Long, User> uploaders,
            Map<Long, AnalysisRequest> latestAnalysis
    ) {
        User uploader = uploaders.get(evidence.getUploaderId());
        AnalysisRequest analysis = latestAnalysis.get(evidence.getEvidenceId());

        return AdminEvidenceItemResponse.builder()
                .id(String.valueOf(evidence.getEvidenceId()))
                .fileName(evidence.getFileName())
                .fileType(evidence.getFileType().name())
                .caseNumber(evidence.getCaseNumber())
                .caseName(evidence.getCaseName())
                .uploaderUsername(uploader == null ? "unknown" : uploader.getLoginId())
                .uploaderName(uploader == null ? "-" : uploader.getName())
                .department(uploader == null ? "-" : uploader.getDepartment())
                .hashValue(evidence.getOriginalHashValue())
                .fileSize(evidence.getFileSize())
                .uploadedAt(formatDateTime(evidence.getUploadedAt()))
                .status(evidence.getStatus().name())
                .analysisStatus(resolveAnalysisStatus(analysis))
                .build();
    }

    private AdminEvidenceMetadataResponse toMetadata(EvidenceMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return AdminEvidenceMetadataResponse.builder()
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .durationSec(metadata.getDurationSec())
                .fps(metadata.getFps())
                .codec(metadata.getCodec())
                .sampleRate(metadata.getSampleRate())
                .channels(metadata.getChannels())
                .deviceInfo(metadata.getDeviceInfo())
                .extractionStatus(metadata.getExtractionStatus().name())
                .build();
    }

    private AdminEvidenceAnalysisResponse toAnalysis(AnalysisRequest request) {
        return AdminEvidenceAnalysisResponse.builder()
                .id(String.valueOf(request.getAnalysisRequestId()))
                .status(request.getStatus().name())
                .requestedAt(formatDateTime(request.getRequestedAt()))
                .completedAt(formatDateTime(request.getCompletedAt()))
                .build();
    }

    private AdminEvidenceCustodyLogResponse toCustodyLog(CustodyLog log, Map<Long, User> actors) {
        User actor = actors.get(log.getActorId());
        return AdminEvidenceCustodyLogResponse.builder()
                .id(String.valueOf(log.getLogId()))
                .timestamp(formatDateTime(log.getCreatedAt()))
                .category(LogCategoryMapper.resolveCategory(log.getActionType()))
                .actor(actor == null ? "unknown" : actor.getLoginId())
                .action(LogCategoryMapper.resolveActionLabel(log.getActionType()))
                .detail(log.getReason())
                .build();
    }

    private String resolveAnalysisStatus(AnalysisRequest analysis) {
        return analysis == null ? "NONE" : analysis.getStatus().name();
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }
}
