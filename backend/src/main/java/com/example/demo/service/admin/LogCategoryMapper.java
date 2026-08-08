package com.example.demo.service.admin;

import java.util.List;
import java.util.Set;

public final class LogCategoryMapper {

    private static final Set<String> AUTH_ACTIONS = Set.of(
            "LOGIN",
            "LOGOUT",
            "SIGNUP_REQUEST",
            "STEP_UP_VERIFIED",
            "STEP_UP_EXTENDED"
    );
    private static final Set<String> ADMIN_ACTIONS = Set.of(
            "USER_APPROVED",
            "USER_REJECTED",
            "USER_SUSPENDED",
            "USER_REACTIVATED",
            "USER_DELETED",
            "USER_PASSWORD_RESET"
    );
    private static final Set<String> ANALYSIS_ACTIONS = Set.of(
            "ANALYSIS_REQUESTED",
            "ANALYSIS_STARTED",
            "ANALYSIS_COMPLETED",
            "ANALYSIS_FAILED",
            "ANALYSIS_CANCELLED"
    );
    private static final Set<String> COC_ACTIONS = Set.of(
            "EVIDENCE_UPLOADED",
            "EVIDENCE_DELETED",
            "HASH_CREATED",
            "METADATA_EXTRACTED",
            "EVIDENCE_VIEWED",
            "ANALYSIS_COPY_CREATED",
            "ANALYSIS_COPY_VERIFIED",
            "ANALYSIS_COPY_DELETED",
            "REPORT_CREATED",
            "REPORT_DOWNLOADED",
            "QUALITY_WARNING_ACKNOWLEDGED",
            "EVIDENCE_HLS_PACKAGED",
            "EVIDENCE_STREAM_ACCESS"
    );

    private LogCategoryMapper() {
    }

    public static String resolveCategory(String actionType) {
        if (AUTH_ACTIONS.contains(actionType)) {
            return "AUTH";
        }
        if (ADMIN_ACTIONS.contains(actionType)) {
            return "ADMIN";
        }
        if (ANALYSIS_ACTIONS.contains(actionType)) {
            return "ANALYSIS";
        }
        if (COC_ACTIONS.contains(actionType)) {
            return "COC";
        }
        return "ADMIN";
    }

    public static List<String> actionTypesForCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        return switch (category.trim().toUpperCase()) {
            case "AUTH" -> List.copyOf(AUTH_ACTIONS);
            case "ADMIN" -> List.copyOf(ADMIN_ACTIONS);
            case "ANALYSIS" -> List.copyOf(ANALYSIS_ACTIONS);
            case "COC" -> List.copyOf(COC_ACTIONS);
            default -> List.of();
        };
    }

    public static List<String> cocActionTypes() {
        return List.copyOf(COC_ACTIONS);
    }

    public static String resolveActionLabel(String actionType) {
        return switch (actionType) {
            case "LOGIN" -> "로그인";
            case "LOGOUT" -> "로그아웃";
            case "SIGNUP_REQUEST" -> "가입 신청";
            case "USER_APPROVED" -> "가입 승인";
            case "USER_REJECTED" -> "가입 반려";
            case "USER_SUSPENDED" -> "계정 정지";
            case "USER_REACTIVATED" -> "계정 재활성";
            case "USER_DELETED" -> "계정 삭제";
            case "USER_PASSWORD_RESET" -> "비밀번호 재설정";
            case "EVIDENCE_UPLOADED" -> "증거 업로드";
            case "EVIDENCE_DELETED" -> "증거 삭제";
            case "HASH_CREATED" -> "해시 생성";
            case "METADATA_EXTRACTED" -> "메타데이터 추출";
            case "EVIDENCE_VIEWED" -> "증거 열람";
            case "ANALYSIS_COPY_CREATED" -> "분석 복사본 생성";
            case "ANALYSIS_COPY_VERIFIED" -> "분석 복사본 검증";
            case "ANALYSIS_COPY_DELETED" -> "분석 복사본 삭제";
            case "ANALYSIS_REQUESTED" -> "분석 요청";
            case "ANALYSIS_STARTED" -> "분석 시작";
            case "ANALYSIS_COMPLETED" -> "분석 완료";
            case "ANALYSIS_FAILED" -> "분석 실패";
            case "ANALYSIS_CANCELLED" -> "분석 중단";
            case "REPORT_CREATED" -> "보고서 생성";
            case "REPORT_DOWNLOADED" -> "보고서 다운로드";
            case "QUALITY_WARNING_ACKNOWLEDGED" -> "화질 안내 확인";
            case "EVIDENCE_HLS_PACKAGED" -> "HLS 패키징";
            case "EVIDENCE_STREAM_ACCESS" -> "HLS 스트림 접근";
            default -> actionType;
        };
    }
}
