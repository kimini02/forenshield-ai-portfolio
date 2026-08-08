package com.example.demo.util;

import com.example.demo.exception.BusinessException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

public final class CaseKeyNormalizer {

    private static final String MISSING_CASE_KEY_MESSAGE = "사건 식별자가 필요합니다.";

    private CaseKeyNormalizer() {
    }

    /**
     * FE 라우트·쿼리에서 이중 인코딩된 사건명을 안전하게 복원합니다.
     */
    public static String normalize(String caseKey) {
        if (caseKey == null) {
            return null;
        }

        String current = caseKey.trim();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String decoded = URLDecoder.decode(current.replace("+", " "), StandardCharsets.UTF_8);
                if (decoded.equals(current)) {
                    break;
                }
                current = decoded.trim();
            } catch (IllegalArgumentException ex) {
                break;
            }
        }
        return current;
    }

    public static String resolveCaseKey(String caseKey, String pathCaseId) {
        return requireCaseKey(caseKey != null ? caseKey : pathCaseId);
    }

    public static String requireCaseKey(String caseKey) {
        String normalized = normalize(caseKey);
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", MISSING_CASE_KEY_MESSAGE);
        }
        return normalized;
    }
}
