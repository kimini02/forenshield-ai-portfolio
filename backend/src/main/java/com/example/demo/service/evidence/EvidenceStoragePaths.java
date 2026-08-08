package com.example.demo.service.evidence;

import com.example.demo.domain.Evidence;

import java.util.Locale;

public final class EvidenceStoragePaths {

    private EvidenceStoragePaths() {
    }

    public static String resolveCaseKey(Evidence evidence) {
        if (evidence.getCaseName() != null && !evidence.getCaseName().isBlank()) {
            return sanitize(evidence.getCaseName());
        }
        return "evidence-" + evidence.getEvidenceId();
    }

    /**
     * S3 object file segment: {@code {caseName}-{evidenceId}.{ext}} — not the user's original filename.
     * DB {@code file_name} keeps the original upload name for display and reports.
     */
    public static String storedObjectFileName(Evidence evidence, String originalFileName) {
        return storedObjectBaseName(evidence) + extractExtension(originalFileName);
    }

    public static String storedObjectBaseName(Evidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("evidence is required");
        }
        if (evidence.getEvidenceId() == null) {
            throw new IllegalArgumentException("evidenceId is required");
        }
        String caseName = sanitizePart(evidence.getCaseName());
        if (caseName.isEmpty()) {
            return "evidence-" + evidence.getEvidenceId();
        }
        return caseName + "-" + evidence.getEvidenceId();
    }

    public static String originalKey(String caseKey, Evidence evidence, String originalFileName) {
        return "cases/" + caseKey + "/" + evidence.getEvidenceId() + "/original/"
                + storedObjectFileName(evidence, originalFileName);
    }

    public static String copyKey(String caseKey, Evidence evidence, String originalFileName) {
        return "cases/" + caseKey + "/" + evidence.getEvidenceId() + "/copy/"
                + storedObjectFileName(evidence, originalFileName);
    }

    public static String manifestKey(String caseKey, Long evidenceId) {
        return "cases/" + caseKey + "/" + evidenceId + "/manifest/evidence-manifest.json";
    }

    /** HLS 재생 전용 prefix (끝에 / 포함) */
    public static String hlsPrefix(Long evidenceId) {
        return "hls/" + evidenceId + "/";
    }

    public static String hlsMasterKey(Long evidenceId) {
        return hlsPrefix(evidenceId) + "master.m3u8";
    }

    private static String sanitizePart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return sanitize(value);
    }

    private static String extractExtension(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "";
        }
        String name = originalFileName.trim();
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == name.length() - 1) {
            return "";
        }
        return name.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String value) {
        return value.trim()
                .replaceAll("[\\\\/]+", "-")
                .replaceAll("\\s+", "-")
                .toLowerCase(Locale.ROOT);
    }
}
