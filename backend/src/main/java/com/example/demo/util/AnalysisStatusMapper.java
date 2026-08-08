package com.example.demo.util;

import com.example.demo.domain.enums.AnalysisStatus;

public final class AnalysisStatusMapper {

    private AnalysisStatusMapper() {
    }

    /** FE polling용 — PENDING · PROCESSING · COMPLETED · FAILED */
    public static String toApiStatus(AnalysisStatus status) {
        if (status == null) {
            return "PENDING";
        }
        return switch (status) {
            case QUEUED -> "PENDING";
            case ANALYZING -> "PROCESSING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }

    /** Jira/SK-923 큐 모니터링용 — WAITING · ANALYZING · COMPLETED · FAILED */
    public static String toQueueStatus(AnalysisStatus status) {
        if (status == null) {
            return "WAITING";
        }
        return switch (status) {
            case QUEUED -> "WAITING";
            case ANALYZING -> "ANALYZING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }

    public static boolean isCompleted(AnalysisStatus status) {
        return status == AnalysisStatus.COMPLETED;
    }
}
