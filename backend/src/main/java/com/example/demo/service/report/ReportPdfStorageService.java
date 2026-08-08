package com.example.demo.service.report;

import com.example.demo.config.ReportPublicUrlProperties;
import com.example.demo.domain.Report;
import com.example.demo.domain.enums.ReportPublicationStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.ReportCustodyLogService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.util.PdfDocumentWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportPdfStorageService {

    private final ReportRepository reportRepository;
    private final HashService hashService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final ReportCustodyLogService reportCustodyLogService;
    private final ReportPublicUrlProperties reportPublicUrlProperties;
    private final ReportPublicationSnapshotService reportPublicationSnapshotService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public Report persistAnalysisReport(
            Long analysisResultId,
            Long evidenceId,
            Long userId,
            String fileName,
            List<String> lines,
            String title
    ) {
        int version = Math.toIntExact(reportRepository.countByEvidenceIdAndCompareIdIsNull(evidenceId) + 1);
        String versionedFileName = withVersion(fileName, version);
        byte[] pdfBytes = PdfDocumentWriter.writeDraftReport(title, lines);
        Path reportPath = storePdf("evidence", evidenceId, versionedFileName, pdfBytes);
        Report report = new Report();
        report.setAnalysisResultId(analysisResultId);
        report.setEvidenceId(evidenceId);
        report.setCreatedBy(userId);
        report.setReportFileName(versionedFileName);
        report.setPublicationStatus(ReportPublicationStatus.DRAFT);
        report.setReportVersion(version);
        report.setStoragePath(reportPath.toString());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.setCreatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    public Report persistCompareReport(
            Long compareId,
            Long evidenceId,
            Long userId,
            String fileName,
            List<String> lines,
            String title
    ) {
        byte[] pdfBytes = PdfDocumentWriter.writeDraftReport(title, lines);
        Path reportPath = storePdf("compare", compareId, fileName, pdfBytes);
        Report report = new Report();
        report.setCompareId(compareId);
        report.setEvidenceId(evidenceId);
        report.setCreatedBy(userId);
        report.setReportFileName(fileName);
        report.setPublicationStatus(ReportPublicationStatus.DRAFT);
        report.setReportVersion(1);
        report.setStoragePath(reportPath.toString());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.setCreatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    public Report issueReport(
            Report report,
            Long userId,
            List<String> lines,
            String title
    ) {
        if (report.isIssued()) {
            return report;
        }

        ReportVerificationPayload verificationPayload = createVerificationPayload();
        byte[] pdfBytes = buildPdfWithQr(
                title,
                lines,
                verificationPayload.reportNo(),
                verificationPayload.verifyUrl(),
                verificationPayload.verificationCode()
        );
        persistReportBytes(report, pdfBytes);

        report.setReportNo(verificationPayload.reportNo());
        report.setVerificationToken(verificationPayload.verificationToken());
        report.setVerificationCode(verificationPayload.verificationCode());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.markIssued(userId, LocalDateTime.now());
        Report saved = reportRepository.save(report);
        reportPublicationSnapshotService.createIfAbsent(saved, lines);
        supersedeOlderAnalysisReports(saved);
        recordReportSideEffects(saved, userId);
        return saved;
    }

    public byte[] readStoredPdf(String storagePath) {
        try {
            return Files.readAllBytes(Paths.get(storagePath));
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_READ_FAILED", "PDF 리포트를 읽을 수 없습니다.");
        }
    }

    /**
     * Prod stores reports under ephemeral pod storage ({@code /tmp/uploads}). After redeploy the DB row
     * may outlive the file; regenerate from current content instead of failing preview/download.
     */
    public byte[] ensureReportPdfBytes(Report report, List<String> lines, String title) {
        if (hasStoredPdf(report.getStoragePath())) {
            return readStoredPdf(report.getStoragePath());
        }

        log.warn(
                "Report PDF missing on disk; regenerating reportId={} evidenceId={} path={}",
                report.getReportId(),
                report.getEvidenceId(),
                report.getStoragePath()
        );
        byte[] pdfBytes = renderReportBytes(report, lines, title);
        persistReportBytes(report, pdfBytes);
        return pdfBytes;
    }

    public byte[] addPreviewWatermark(byte[] pdfBytes) {
        return PdfDocumentWriter.addPreviewWatermark(pdfBytes);
    }

    public boolean verifyStoredFileHash(Report report) {
        if (report.getReportHash() == null || report.getStoragePath() == null) {
            return false;
        }
        try {
            byte[] stored = Files.readAllBytes(Paths.get(report.getStoragePath()));
            return report.getReportHash().equalsIgnoreCase(hashService.generateSha256(stored));
        } catch (IOException ex) {
            return false;
        }
    }

    private byte[] buildPdfWithQr(
            String title,
            List<String> lines,
            String reportNo,
            String qrContent,
            String verificationCode
    ) {
        return PdfDocumentWriter.writeReport(title, lines, qrContent, verificationCode, reportNo);
    }

    private void recordReportSideEffects(Report report, Long userId) {
        try {
            blockchainAnchorService.anchorReportHash(report, userId);
        } catch (Exception ex) {
            log.warn("Report hash blockchain anchor failed reportId={} evidenceId={}: {}",
                    report.getReportId(), report.getEvidenceId(), ex.getMessage());
        }

        try {
            reportCustodyLogService.recordReportCreated(userId, report);
        } catch (Exception ex) {
            log.warn("Report custody log failed reportId={} evidenceId={}: {}",
                    report.getReportId(), report.getEvidenceId(), ex.getMessage());
        }
    }

    private ReportVerificationPayload createVerificationPayload() {
        String token = "vrf_" + UUID.randomUUID().toString().replace("-", "");
        String suffix = token.substring(token.length() - 8).toUpperCase(Locale.ROOT);
        String reportNo = "RPT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
        String codeSeed = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String verificationCode = "VF-" + codeSeed.substring(0, 4) + "-" + codeSeed.substring(4);
        return new ReportVerificationPayload(reportNo, token, verificationCode, buildVerifyUrl(token));
    }

    private String buildVerifyUrl(String token) {
        return reportPublicUrlProperties.verificationUrl(token);
    }

    private byte[] renderReportBytes(Report report, List<String> lines, String title) {
        List<String> renderLines = report.isIssued()
                ? reportPublicationSnapshotService.findReportLines(report).orElse(lines)
                : lines;
        if (report.isIssued() && report.getVerificationToken() != null && !report.getVerificationToken().isBlank()) {
            return buildPdfWithQr(
                    title,
                    renderLines,
                    report.getReportNo(),
                    buildVerifyUrl(report.getVerificationToken()),
                    report.getVerificationCode()
            );
        }
        return PdfDocumentWriter.writeDraftReport(title, renderLines);
    }

    private void persistReportBytes(Report report, byte[] pdfBytes) {
        if (hasStoredPdf(report.getStoragePath())) {
            overwritePdf(report.getStoragePath(), pdfBytes);
        } else {
            String category = report.getCompareId() != null ? "compare" : "evidence";
            Long id = report.getCompareId() != null ? report.getCompareId() : report.getEvidenceId();
            Path reportPath = storePdf(category, id, report.getReportFileName(), pdfBytes);
            report.setStoragePath(reportPath.toString());
        }
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        reportRepository.save(report);
    }

    private boolean hasStoredPdf(String storagePath) {
        return storagePath != null && !storagePath.isBlank() && Files.exists(Paths.get(storagePath));
    }

    private Path storePdf(String category, Long id, String fileName, byte[] pdfBytes) {
        try {
            Path reportDir = Paths.get(uploadDir, "reports", category, String.valueOf(id));
            Files.createDirectories(reportDir);
            Path reportPath = reportDir.resolve(fileName);
            Files.write(reportPath, pdfBytes);
            return reportPath;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_SAVE_FAILED", "PDF 리포트 저장에 실패했습니다.");
        }
    }

    private void overwritePdf(String storagePath, byte[] pdfBytes) {
        try {
            Files.write(Paths.get(storagePath), pdfBytes);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_SAVE_FAILED", "최종 PDF 리포트 저장에 실패했습니다.");
        }
    }

    private void supersedeOlderAnalysisReports(Report issuedReport) {
        if (issuedReport.getAnalysisResultId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Report> superseded = reportRepository
                .findByEvidenceIdAndCompareIdIsNullOrderByCreatedAtDesc(issuedReport.getEvidenceId())
                .stream()
                .filter(candidate -> !candidate.getReportId().equals(issuedReport.getReportId()))
                .filter(Report::isIssued)
                .toList();
        superseded.forEach(report -> report.markSuperseded(now));
        reportRepository.saveAll(superseded);
    }

    private String withVersion(String fileName, int version) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0) {
            return fileName + "-v" + version;
        }
        return fileName.substring(0, extensionIndex) + "-v" + version + fileName.substring(extensionIndex);
    }

    private record ReportVerificationPayload(
            String reportNo,
            String verificationToken,
            String verificationCode,
            String verifyUrl
    ) {
    }
}
