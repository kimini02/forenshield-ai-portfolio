package com.example.demo.service.report;

import com.example.demo.config.ReportPublicUrlProperties;
import com.example.demo.service.compare.CompareVerificationAssembler;
import com.example.demo.service.compare.CompareVerificationService;
import com.example.demo.service.custody.ReportCustodyLogService;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.domain.enums.ReportPublicationStatus;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.dto.PublicReportAccessIssueResponse;
import com.example.demo.dto.PublicReportFileHashVerifyResponse;
import com.example.demo.dto.PublicReportVerifyResponse;
import com.example.demo.dto.PublicReportViewResponse;
import com.example.demo.dto.ReportVerifyResponse;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ACCESS_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final EvidenceAccessService evidenceAccessService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final CaseProfileRepository caseProfileRepository;
    private final ReportRepository reportRepository;
    private final CompareVerificationService compareVerificationService;
    private final CompareVerificationAssembler compareVerificationAssembler;
    private final ReportContentBuilder reportContentBuilder;
    private final ReportPdfStorageService reportPdfStorageService;
    private final ReportCustodyLogService reportCustodyLogService;
    private final ReportPdfPersistenceService reportPdfPersistenceService;
    private final EvidenceManifestService evidenceManifestService;
    private final BlockchainAnchorRepository blockchainAnchorRepository;
    private final ReportPublicUrlProperties reportPublicUrlProperties;
    private final ReportPublicationSnapshotService reportPublicationSnapshotService;

    public ReportPdfPayload generateEvidenceReport(User user, Long evidenceId) {
        return generateEvidenceReport(user, evidenceId, false);
    }

    public ReportPdfPayload generateEvidenceReport(User user, Long evidenceId, boolean preview) {
        Evidence evidence = evidenceAccessService.requireReadable(user, evidenceId);
        AnalysisRequest request = requireCompletedAnalysis(evidenceId);
        AnalysisResult result = requireAnalysisResult(request.getAnalysisRequestId());
        List<AnalysisModuleResult> modules = analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());

        return reportPdfPersistenceService.persistEvidenceReport(
                user,
                evidenceId,
                preview,
                evidence,
                request,
                result,
                modules,
                isApprovedForReport(evidence)
        );
    }

    public ReportPdfPayload generateCompareReport(User user, Long compareId) {
        return generateCompareReport(user, compareId, false);
    }

    public ReportPdfPayload generateCompareReport(User user, Long compareId, boolean preview) {
        CompareVerification verification = compareVerificationService.requireOwnedVerification(user, compareId);
        List<CompareItemDto> items = compareVerificationAssembler.deserializeItems(verification.getResultJson());
        Evidence original = evidenceAccessService.requireOwned(user, verification.getOriginalEvidenceId());
        CompareFileInfoDto originalInfo = compareVerificationService.getOriginalFileInfo(
                user,
                verification.getOriginalEvidenceId()
        );
        CompareFileInfoDto candidateInfo = compareVerificationService.getCandidateFileInfo(user, compareId);

        return reportPdfPersistenceService.persistCompareReport(
                user,
                compareId,
                preview,
                verification,
                original,
                originalInfo,
                candidateInfo,
                items,
                isApprovedForReport(original)
        );
    }

    @Transactional
    public void issueCaseReports(User reviewer, List<Evidence> evidences) {
        for (Evidence evidence : evidences) {
            AnalysisRequest request = analysisRequestRepository
                    .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                    .filter(candidate -> candidate.getStatus() == AnalysisStatus.COMPLETED)
                    .orElse(null);
            if (request == null) {
                continue;
            }

            AnalysisResult result = analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId())
                    .orElse(null);
            if (result == null) {
                continue;
            }

            List<AnalysisModuleResult> modules = analysisModuleResultRepository
                    .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());
            List<String> lines = reportContentBuilder.buildEvidenceLines(evidence, request, result, modules);
            Report report = reportRepository
                    .findTopByAnalysisResultIdOrderByCreatedAtDesc(result.getAnalysisResultId())
                    .orElseGet(() -> reportPdfStorageService.persistAnalysisReport(
                            result.getAnalysisResultId(),
                            evidence.getEvidenceId(),
                            evidence.getUploaderId(),
                            "analysis-report-" + evidence.getEvidenceId() + ".pdf",
                            lines,
                            "ForenShield Analysis Report"
                    ));
            if (!report.isIssued()) {
                reportPdfStorageService.issueReport(
                        report,
                        reviewer.getUserId(),
                        lines,
                        "ForenShield Analysis Report"
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public ReportVerifyResponse verifyReportHash(User user, Long evidenceId, String reportHash) {
        evidenceAccessService.requireReadable(user, evidenceId);
        if (reportHash == null || reportHash.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "reportHash는 필수입니다.");
        }

        Report report = reportRepository.findByEvidenceIdAndReportHash(evidenceId, reportHash.trim())
                .orElseGet(() -> reportRepository.findTopByEvidenceIdOrderByCreatedAtDesc(evidenceId)
                        .filter(candidate -> reportHash.trim().equalsIgnoreCase(candidate.getReportHash()))
                        .orElse(null));

        if (report == null) {
            return ReportVerifyResponse.builder()
                    .valid(false)
                    .evidenceId(evidenceId)
                    .reportHash(reportHash.trim())
                    .message("일치하는 리포트를 찾을 수 없습니다.")
                    .build();
        }

        boolean valid = reportPdfStorageService.verifyStoredFileHash(report);
        return ReportVerifyResponse.builder()
                .valid(valid)
                .reportId(report.getReportId())
                .evidenceId(evidenceId)
                .reportHash(report.getReportHash())
                .reportFileName(report.getReportFileName())
                .createdAt(ApiDateTimeFormatter.formatUtc(report.getCreatedAt()))
                .message(valid ? "reportHash가 저장된 PDF와 일치합니다." : "reportHash가 저장된 PDF와 일치하지 않습니다.")
                .build();
    }

    @Transactional(readOnly = true)
    public PublicReportVerifyResponse verifyPublicReport(String token, String code) {
        Optional<Report> optionalReport = resolvePublicReport(token, code);
        if (optionalReport.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "REPORT_VERIFICATION_NOT_FOUND",
                    "등록되지 않은 검증 정보입니다."
            );
        }

        Report report = optionalReport.get();
        if (!report.isIssued()) {
            return pendingPublicationResponse(report);
        }

        boolean hashMatched = reportPdfStorageService.verifyStoredFileHash(report);
        SignatureSnapshot signature = resolveSignature(report.getEvidenceId());
        BlockchainSnapshot blockchain = resolveBlockchain(report);
        ReportPublicationSnapshotService.PublicSummary publicSummary = reportPublicationSnapshotService
                .findPublicSummary(report)
                .orElse(null);

        // QR lookup confirms an issuance-registry record only. The visitor's PDF bytes are not inspected
        // until the separate file-hash comparison is performed.
        boolean superseded = report.getPublicationStatus() == ReportPublicationStatus.SUPERSEDED;
        String status = superseded ? "WARNING" : "VALID";
        String message = superseded
                ? "발행 등록정보를 조회했습니다. 이 보고서는 더 최신 발행본으로 대체되었습니다. PDF 파일 자체는 검사하지 않았습니다."
                : "발행 등록정보를 조회했습니다. PDF 파일 자체는 아직 검사하지 않았습니다.";

        return PublicReportVerifyResponse.builder()
                .status(status)
                .valid(true)
                .message(message)
                .reportId(report.getReportId())
                .reportNo(report.getReportNo())
                .verificationCode(report.getVerificationCode())
                .reportType(report.getCompareId() == null ? "ANALYSIS" : "COMPARE")
                .revision(report.getReportVersion())
                .publicationStatus(report.getPublicationStatus().name())
                .issuedAt(formatDateTime(report.getIssuedAt()))
                .queriedAt(formatDateTime(LocalDateTime.now()))
                .pdfSignatureApplied(false)
                .evidenceId(report.getEvidenceId())
                .reportHash(report.getReportHash())
                .reportFileName(report.getReportFileName())
                .createdAt(formatDateTime(report.getCreatedAt()))
                .analysisVerdict(publicSummary == null ? null : publicSummary.verdict())
                .analysisCompletedAt(publicSummary == null ? null : publicSummary.analysisCompletedAt())
                .snapshotSchemaVersion(publicSummary == null ? null : publicSummary.snapshotSchemaVersion())
                .pdfTemplateVersion(publicSummary == null ? null : publicSummary.pdfTemplateVersion())
                .hashMatched(hashMatched)
                .storedFileIntact(hashMatched)
                .signatureValid(signature.valid())
                .signatureStatus(signature.status())
                .signatureAlgorithm(signature.algorithm())
                .signerCertificateSubject(signature.signerCertificateSubject())
                .evidenceManifestSignatureValid(publicSummary == null
                        || publicSummary.evidenceManifestSignatureStatus() == null
                        ? signature.valid()
                        : publicSummary.evidenceManifestSignatureValid())
                .evidenceManifestSignatureStatus(publicSummary == null
                        || publicSummary.evidenceManifestSignatureStatus() == null
                        ? signature.status()
                        : publicSummary.evidenceManifestSignatureStatus())
                .evidenceManifestSignatureAlgorithm(publicSummary == null
                        || publicSummary.evidenceManifestSignatureStatus() == null
                        ? signature.algorithm()
                        : publicSummary.evidenceManifestSignatureAlgorithm())
                .evidenceManifestSignerCertificateSubject(publicSummary == null
                        || publicSummary.evidenceManifestSignatureStatus() == null
                        ? signature.signerCertificateSubject()
                        : publicSummary.evidenceManifestSignerCertificateSubject())
                .blockchainMatched(blockchain.matched())
                .blockchainStatus(blockchain.status())
                .blockchainTxHash(blockchain.transactionHash())
                .blockchainNetwork(blockchain.network())
                .blockchainAnchoredAt(blockchain.anchoredAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PublicReportFileHashVerifyResponse verifyPublicReportFileHash(
            String token,
            String code,
            String fileHash
    ) {
        Report report = resolvePublicReport(token, code)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "REPORT_VERIFICATION_NOT_FOUND",
                        "등록되지 않은 검증 정보입니다."
                ));
        if (!report.isIssued()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_NOT_ISSUED",
                    "아직 발행되지 않은 보고서입니다."
            );
        }

        String normalizedHash = normalizeSha256(fileHash);
        boolean storedFileIntact = reportPdfStorageService.verifyStoredFileHash(report);
        boolean matched = report.getReportHash() != null
                && report.getReportHash().equalsIgnoreCase(normalizedHash);

        String status;
        String message;
        if (!matched) {
            status = "MISMATCH";
            message = "선택한 PDF가 발급 시 등록된 최종 파일과 일치하지 않습니다.";
        } else if (!storedFileIntact) {
            status = "WARNING";
            message = "선택한 PDF 해시는 등록값과 일치하지만 서버 보관 원본 상태를 확인할 수 없습니다.";
        } else {
            status = "MATCH";
            message = "선택한 PDF가 발급 시 등록된 최종 파일과 일치합니다.";
        }

        return PublicReportFileHashVerifyResponse.builder()
                .status(status)
                .matched(matched)
                .storedFileIntact(storedFileIntact)
                .message(message)
                .reportNo(report.getReportNo())
                .submittedHash(normalizedHash)
                .registeredHash(report.getReportHash())
                .build();
    }

    @Transactional
    public PublicReportAccessIssueResponse issuePublicReportAccess(User user, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "REPORT_NOT_FOUND",
                        "보고서를 찾을 수 없습니다."
                ));
        ensureCanIssuePublicAccess(user, report);

        LocalDateTime now = LocalDateTime.now();
        String accessCode = report.getPublicAccessCode();
        if (accessCode == null || accessCode.isBlank() || isExpired(report.getPublicAccessExpiresAt())) {
            accessCode = generateUniquePublicAccessCode();
            report.setPublicAccessCode(accessCode);
        }

        report.setPublicAccessEnabled(true);
        report.setPublicAccessIssuedAt(now);
        report.setPublicAccessExpiresAt(now.plusDays(reportPublicUrlProperties.getPublicAccessTtlDays()));
        Report saved = reportRepository.save(report);
        return toPublicAccessIssueResponse(saved);
    }

    @Transactional(readOnly = true)
    public PublicReportViewResponse getPublicReportView(String accessCode) {
        Report report = requirePublicAccessReport(accessCode);
        return toPublicReportViewResponse(report);
    }

    @Transactional(readOnly = true)
    public ReportPdfPayload downloadPublicReport(String accessCode) {
        Report report = requirePublicAccessReport(accessCode);
        byte[] pdfBytes = reportPdfStorageService.readStoredPdf(report.getStoragePath());
        return toPayload(report, pdfBytes);
    }

    private Optional<Report> resolvePublicReport(String token, String code) {
        if (token != null && !token.isBlank()) {
            return reportRepository.findByVerificationToken(token.trim());
        }
        if (code != null && !code.isBlank()) {
            return reportRepository.findByVerificationCode(normalizeVerificationCode(code));
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "token 또는 code는 필수입니다.");
    }

    private PublicReportVerifyResponse pendingPublicationResponse(Report report) {
        return PublicReportVerifyResponse.builder()
                .status("PENDING")
                .valid(false)
                .message("아직 발행되지 않은 보고서입니다. 검토 승인과 발행 등록이 완료된 후 다시 확인해 주세요.")
                .reportId(report.getReportId())
                .reportNo(report.getReportNo())
                .reportType(report.getCompareId() == null ? "ANALYSIS" : "COMPARE")
                .revision(report.getReportVersion())
                .publicationStatus(report.getPublicationStatus().name())
                .queriedAt(formatDateTime(LocalDateTime.now()))
                .pdfSignatureApplied(false)
                .evidenceId(report.getEvidenceId())
                .reportFileName(report.getReportFileName())
                .createdAt(formatDateTime(report.getCreatedAt()))
                .hashMatched(false)
                .storedFileIntact(false)
                .signatureValid(null)
                .signatureStatus("NOT_ISSUED")
                .evidenceManifestSignatureValid(null)
                .evidenceManifestSignatureStatus("NOT_ISSUED")
                .blockchainMatched(null)
                .blockchainStatus("NOT_ISSUED")
                .build();
    }

    private String normalizeVerificationCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        String compact = normalized.replaceAll("[^A-Z0-9]", "");
        if (compact.startsWith("VF")) {
            compact = compact.substring(2);
        }
        if (compact.length() == 8) {
            return "VF-" + compact.substring(0, 4) + "-" + compact.substring(4);
        }
        return normalized;
    }

    private String normalizeSha256(String fileHash) {
        if (fileHash == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FILE_HASH", "PDF SHA-256 해시는 필수입니다.");
        }
        String normalized = fileHash.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FILE_HASH",
                    "PDF SHA-256 해시 형식이 올바르지 않습니다."
            );
        }
        return normalized;
    }

    private void ensureCanIssuePublicAccess(User user, Report report) {
        if (!report.isIssued()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_NOT_ISSUED",
                    "검토 승인 후 발행된 보고서만 외부 열람코드를 발급할 수 있습니다."
            );
        }
        if (Objects.equals(report.getCreatedBy(), user.getUserId())) {
            return;
        }
        UserRole role = user.getRole();
        if (role == UserRole.ROLE_ADMIN || role == UserRole.ROLE_ORG_ADMIN || role == UserRole.ROLE_REVIEWER) {
            return;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "REPORT_ACCESS_FORBIDDEN", "열람코드 발급 권한이 없습니다.");
    }

    private Report requirePublicAccessReport(String accessCode) {
        if (accessCode == null || accessCode.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "accessCode는 필수입니다.");
        }

        Report report = reportRepository.findByPublicAccessCode(normalizePublicAccessCode(accessCode))
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "REPORT_ACCESS_NOT_FOUND",
                        "등록되지 않은 열람코드입니다."
                ));

        if (!Boolean.TRUE.equals(report.getPublicAccessEnabled())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "REPORT_ACCESS_DISABLED", "비활성화된 열람코드입니다.");
        }
        if (isExpired(report.getPublicAccessExpiresAt())) {
            throw new BusinessException(HttpStatus.GONE, "REPORT_ACCESS_EXPIRED", "만료된 열람코드입니다.");
        }
        if (!report.isIssued()) {
            throw new BusinessException(HttpStatus.GONE, "REPORT_ACCESS_REVOKED", "더 이상 유효하지 않은 보고서입니다.");
        }
        return report;
    }

    private String normalizePublicAccessCode(String accessCode) {
        String normalized = accessCode.trim().toUpperCase(Locale.ROOT);
        String compact = normalized.replaceAll("[^A-Z0-9]", "");
        if (compact.startsWith("RV")) {
            compact = compact.substring(2);
        }
        if (compact.length() == 8) {
            return "RV-" + compact.substring(0, 4) + "-" + compact.substring(4);
        }
        return normalized;
    }

    private String generateUniquePublicAccessCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String compact = randomAccessCodePart(8);
            String code = "RV-" + compact.substring(0, 4) + "-" + compact.substring(4);
            if (reportRepository.findByPublicAccessCode(code).isEmpty()) {
                return code;
            }
        }
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_ACCESS_CODE_FAILED", "열람코드 생성에 실패했습니다.");
    }

    private String randomAccessCodePart(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(ACCESS_CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(ACCESS_CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    private PublicReportAccessIssueResponse toPublicAccessIssueResponse(Report report) {
        return PublicReportAccessIssueResponse.builder()
                .reportId(report.getReportId())
                .reportNo(report.getReportNo())
                .accessCode(report.getPublicAccessCode())
                .enabled(Boolean.TRUE.equals(report.getPublicAccessEnabled()))
                .publicViewUrl(buildPublicViewUrl(report.getPublicAccessCode()))
                .issuedAt(formatDateTime(report.getPublicAccessIssuedAt()))
                .expiresAt(formatDateTime(report.getPublicAccessExpiresAt()))
                .build();
    }

    private PublicReportViewResponse toPublicReportViewResponse(Report report) {
        return PublicReportViewResponse.builder()
                .reportId(report.getReportId())
                .reportNo(report.getReportNo())
                .reportType(report.getCompareId() != null ? "COMPARE" : "ANALYSIS")
                .evidenceId(report.getEvidenceId())
                .compareId(report.getCompareId())
                .reportFileName(report.getReportFileName())
                .reportHash(report.getReportHash())
                .fileSize(report.getFileSize())
                .createdAt(formatDateTime(report.getCreatedAt()))
                .expiresAt(formatDateTime(report.getPublicAccessExpiresAt()))
                .downloadPath("/api/v1/public/reports/view/pdf?code="
                        + URLEncoder.encode(report.getPublicAccessCode(), StandardCharsets.UTF_8))
                .build();
    }

    private String buildPublicViewUrl(String accessCode) {
        return reportPublicUrlProperties.publicViewUrl(accessCode);
    }

    private AnalysisRequest requireCompletedAnalysis(Long evidenceId) {
        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT, "ANALYSIS_NOT_FOUND", "분석 요청이 없습니다."));

        if (request.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "ANALYSIS_NOT_COMPLETED", "분석이 완료된 후 PDF 리포트를 생성할 수 있습니다.");
        }
        return request;
    }

    private AnalysisResult requireAnalysisResult(Long analysisRequestId) {
        return analysisResultRepository.findByAnalysisRequestId(analysisRequestId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT, "ANALYSIS_RESULT_NOT_FOUND", "분석 결과가 없습니다."));
    }

    private boolean isApprovedForReport(Evidence evidence) {
        String caseKey = EvidenceCaseIdResolver.resolve(evidence);
        return caseProfileRepository.findByUploaderIdAndCaseKey(evidence.getUploaderId(), caseKey)
                .filter(profile -> profile.getReviewStatus() == CaseReviewStatus.REPORT_APPROVED)
                .isPresent();
    }

    private ReportPdfPayload toPayload(Report report, byte[] pdfBytes) {
        return new ReportPdfPayload(
                report.getReportFileName(),
                pdfBytes,
                report.getReportHash(),
                report.getPublicationStatus().name(),
                report.getReportVersion()
        );
    }

    private SignatureSnapshot resolveSignature(Long evidenceId) {
        return evidenceManifestService.findByEvidenceId(evidenceId)
                .map(manifest -> new SignatureSnapshot(
                        evidenceManifestService.isSignatureValid(manifest),
                        resolveSignatureStatus(manifest),
                        manifest.getSignatureAlgorithm(),
                        manifest.getSignerCertificateSubject()))
                .orElseGet(() -> new SignatureSnapshot(null, "NOT_FOUND", null, null));
    }

    private String resolveSignatureStatus(EvidenceManifest manifest) {
        SignatureStatus status = manifest.getSignatureStatus();
        return status == null ? "UNKNOWN" : status.name();
    }

    private BlockchainSnapshot resolveBlockchain(Report report) {
        return blockchainAnchorRepository
                .findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                        report.getReportId(),
                        BlockchainAnchorType.REPORT_HASH
                )
                .map(anchor -> toBlockchainSnapshot(report, anchor))
                .orElseGet(() -> new BlockchainSnapshot(null, "NOT_ANCHORED", null, null, null));
    }

    private BlockchainSnapshot toBlockchainSnapshot(Report report, BlockchainAnchor anchor) {
        boolean hashMatches = report.getReportHash() != null
                && report.getReportHash().equalsIgnoreCase(anchor.getSubjectHash());
        Boolean matched = anchor.getStatus() == BlockchainAnchorStatus.ANCHORED
                ? hashMatches
                : null;
        return new BlockchainSnapshot(
                matched,
                anchor.getStatus() == null ? "UNKNOWN" : anchor.getStatus().name(),
                anchor.getTransactionHash(),
                anchor.getNetwork(),
                formatDateTime(anchor.getAnchoredAt())
        );
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : ApiDateTimeFormatter.formatUtc(dateTime);
    }

    public record ReportPdfPayload(
            String fileName,
            byte[] content,
            String reportHash,
            String publicationStatus,
            Integer version
    ) {
    }

    private record SignatureSnapshot(
            Boolean valid,
            String status,
            String algorithm,
            String signerCertificateSubject
    ) {
    }

    private record BlockchainSnapshot(
            Boolean matched,
            String status,
            String transactionHash,
            String network,
            String anchoredAt
    ) {
    }
}
