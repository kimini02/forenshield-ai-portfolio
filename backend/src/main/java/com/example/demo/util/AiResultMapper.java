package com.example.demo.util;

public final class AiResultMapper {

    private AiResultMapper() {
    }

    /** FE {@code AiResult} label mapping (mock forensic-api thresholds). */
    public static String fromRiskScore(Double riskScore) {
        if (riskScore == null) {
            return null;
        }
        if (riskScore >= 80) {
            return "위험";
        }
        if (riskScore >= 50) {
            return "검토 필요";
        }
        return "낮음";
    }
}
