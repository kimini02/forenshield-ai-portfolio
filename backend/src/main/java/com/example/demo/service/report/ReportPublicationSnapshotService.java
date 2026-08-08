package com.example.demo.service.report;

import com.example.demo.domain.Report;
import com.example.demo.domain.ReportPublicationSnapshot;
import com.example.demo.repository.ReportPublicationSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportPublicationSnapshotService {

    public static final String SCHEMA_VERSION = "1.1";
    public static final String ANALYSIS_TEMPLATE_VERSION = "analysis-5p-v3";
    public static final String COMPARE_TEMPLATE_VERSION = "compare-2p-v1";

    private final ReportPublicationSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReportPublicationSnapshot createIfAbsent(Report report, List<String> lines) {
        return snapshotRepository.findByReportId(report.getReportId())
                .orElseGet(() -> snapshotRepository.save(buildSnapshot(report, lines)));
    }

    @Transactional(readOnly = true)
    public Optional<List<String>> findReportLines(Report report) {
        return snapshotRepository.findByReportId(report.getReportId())
                .map(ReportPublicationSnapshot::getReportInputJson)
                .map(this::readLines);
    }

    @Transactional(readOnly = true)
    public Optional<PublicSummary> findPublicSummary(Report report) {
        return snapshotRepository.findByReportId(report.getReportId())
                .map(snapshot -> readPublicSummary(snapshot.getPublicSummaryJson(), snapshot));
    }

    private ReportPublicationSnapshot buildSnapshot(Report report, List<String> lines) {
        try {
            List<String> immutableLines = List.copyOf(lines == null ? List.of() : lines);
            String reportInputJson = objectMapper.writeValueAsString(immutableLines);
            String publicSummaryJson = objectMapper.writeValueAsString(publicSummary(report, immutableLines));
            String artifactManifestJson = objectMapper.writeValueAsString(artifactManifest(immutableLines));
            String displayPolicyJson = objectMapper.writeValueAsString(displayPolicy());
            String canonical = String.join("\n",
                    SCHEMA_VERSION,
                    templateVersion(report),
                    reportInputJson,
                    publicSummaryJson,
                    artifactManifestJson,
                    displayPolicyJson
            );
            return ReportPublicationSnapshot.create(
                    report.getReportId(),
                    SCHEMA_VERSION,
                    templateVersion(report),
                    reportInputJson,
                    publicSummaryJson,
                    artifactManifestJson,
                    displayPolicyJson,
                    sha256(canonical),
                    LocalDateTime.now()
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("보고서 발행 스냅샷 직렬화에 실패했습니다.", ex);
        }
    }

    private Map<String, Object> publicSummary(Report report, List<String> lines) {
        Map<String, String> values = rootValues(lines);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportType", report.getCompareId() == null ? "ANALYSIS" : "COMPARE");
        summary.put("verdict", publicVerdict(report, values));
        summary.put("analysisCompletedAt", values.get("Analyzed At"));
        summary.put("integrity", integritySummary(values));
        summary.put("scoreVisible", false);
        summary.put("sensitiveEvidenceVisible", false);
        return summary;
    }

    private Map<String, Object> integritySummary(Map<String, String> values) {
        Map<String, Object> integrity = new LinkedHashMap<>();
        integrity.put("verifiedAt", nullableValue(values.get("Integrity Verified At")));
        integrity.put("manifestSignatureStatus", nullableValue(values.get("Manifest Signature Status")));
        integrity.put("manifestSignatureAlgorithm", nullableValue(values.get("Manifest Signature Algorithm")));
        integrity.put(
                "manifestSignerCertificateSubject",
                nullableValue(values.get("Manifest Signer Certificate Subject"))
        );
        integrity.put("cocChainStatus", nullableValue(values.get("CoC Chain Status")));
        integrity.put("cocLogCount", nullableValue(values.get("CoC Log Count")));
        integrity.put("evidenceBlockchainStatus", nullableValue(values.get("Evidence Blockchain Status")));
        return integrity;
    }

    private Map<String, Object> artifactManifest(List<String> lines) {
        List<String> suspiciousSegments = new ArrayList<>();
        List<String> representativeFrames = new ArrayList<>();
        List<String> moduleTimelines = new ArrayList<>();
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (value.startsWith("Suspicious Segment:")) {
                suspiciousSegments.add(value);
            } else if (value.startsWith("Representative Frame:")) {
                representativeFrames.add(value);
            } else if (value.startsWith("Module Timeline:")) {
                moduleTimelines.add(value);
            }
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("suspiciousSegments", suspiciousSegments);
        manifest.put("representativeFrames", representativeFrames);
        manifest.put("moduleTimelines", moduleTimelines);
        return manifest;
    }

    private Map<String, Object> displayPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("publicFields", List.of(
                "reportNo", "reportType", "revision", "publicationStatus", "issuedAt",
                "verdict", "analysisCompletedAt", "manifestSignatureStatus",
                "manifestSignatureValid", "manifestSignatureAlgorithm"
        ));
        policy.put("privateFields", List.of(
                "caseName", "fileName", "actorNames", "numericScores", "segments", "frames"
        ));
        return policy;
    }

    private Map<String, String> rootValues(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.startsWith("---") || line.startsWith("===")) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator > 0) {
                values.putIfAbsent(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        return values;
    }

    private String publicVerdict(Report report, Map<String, String> values) {
        if (report.getCompareId() != null) {
            return switch (values.getOrDefault("Verdict", "").toUpperCase()) {
                case "MATCH", "MATCHED", "IDENTICAL" -> "원본 일치";
                case "TAMPERED", "MISMATCH", "MISMATCHED" -> "위변조 의심";
                default -> "판정 불가";
            };
        }
        return switch (values.getOrDefault("Risk Level", "").toUpperCase()) {
            case "HIGH" -> "의심 신호 확인";
            case "LOW" -> "의심 신호 미확인";
            default -> "판정 불가";
        };
    }

    private String templateVersion(Report report) {
        return report.getCompareId() == null ? ANALYSIS_TEMPLATE_VERSION : COMPARE_TEMPLATE_VERSION;
    }

    private List<String> readLines(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("보고서 발행 스냅샷을 읽을 수 없습니다.", ex);
        }
    }

    private PublicSummary readPublicSummary(String json, ReportPublicationSnapshot snapshot) {
        try {
            Map<String, Object> values = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Map<String, Object> integrity = mapValue(values.get("integrity"));
            return new PublicSummary(
                    stringValue(values.get("verdict")),
                    stringValue(values.get("analysisCompletedAt")),
                    snapshot.getSchemaVersion(),
                    snapshot.getPdfTemplateVersion(),
                    stringValue(integrity.get("manifestSignatureStatus")),
                    stringValue(integrity.get("manifestSignatureAlgorithm")),
                    stringValue(integrity.get("manifestSignerCertificateSubject"))
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("공개 보고서 요약 스냅샷을 읽을 수 없습니다.", ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String nullableValue(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? null : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", ex);
        }
    }

    public record PublicSummary(
            String verdict,
            String analysisCompletedAt,
            String snapshotSchemaVersion,
            String pdfTemplateVersion,
            String evidenceManifestSignatureStatus,
            String evidenceManifestSignatureAlgorithm,
            String evidenceManifestSignerCertificateSubject
    ) {
        public Boolean evidenceManifestSignatureValid() {
            if (evidenceManifestSignatureStatus == null) {
                return null;
            }
            return switch (evidenceManifestSignatureStatus) {
                case "VALID" -> true;
                case "INVALID", "FAILED" -> false;
                default -> null;
            };
        }
    }
}
