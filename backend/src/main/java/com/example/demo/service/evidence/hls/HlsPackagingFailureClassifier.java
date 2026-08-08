package com.example.demo.service.evidence.hls;

import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * HLS 패키징 실패를 재시도 가능/불가로 분류한다.
 * {@link #PERMANENT_PREFIX} 가 붙은 {@code evidence_hls.hls_error} 는 백필 대상에서 제외한다.
 */
public final class HlsPackagingFailureClassifier {

    public static final String PERMANENT_PREFIX = "PERMANENT:";

    private HlsPackagingFailureClassifier() {
    }

    public static boolean isPermanent(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoSuchKeyException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("nosuchkey")
                        || normalized.contains("the specified key does not exist")
                        || normalized.contains("evidence not found")
                        || normalized.contains("only supported for video")
                        || normalized.contains("error opening input file")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean isPermanentStoredError(String hlsError) {
        return hlsError != null && hlsError.startsWith(PERMANENT_PREFIX);
    }

    public static String toStoredError(String errorMessage, boolean permanent) {
        String normalized = errorMessage == null || errorMessage.isBlank() ? "unknown error" : errorMessage;
        if (permanent) {
            return PERMANENT_PREFIX + " " + normalized;
        }
        return normalized;
    }
}
