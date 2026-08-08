package com.example.demo.domain.enums;

/**
 * 영상 AI 분석 적합성 등급. 위변조 판별이 아닌 화질·메타 기반 사전 안내용.
 */
public enum ReadinessTier {
    GOOD,
    CAUTION,
    POOR,
    BLOCK
}
