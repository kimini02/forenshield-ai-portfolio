package com.example.demo.domain.enums;

/**
 * 업로드·분석 대상 미디어 타입. DB의 file_type 컬럼 값과 1:1 매핑된다.
 * VIDEO 외에도 DB에 IMAGE/AUDIO 행이 존재할 수 있으므로 enum에 모두 정의해
 * Hibernate 로딩 시 "No enum constant FileType.IMAGE" 예외를 막는다.
 */
public enum FileType {
    VIDEO,
    AUDIO,
    IMAGE
}
