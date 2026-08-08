package com.example.demo.domain.enums;

// readiness_json 산출 경로.

public enum ReadinessSource {
    // 업로드 직후 ffprobe 메타만으로 즉시 판정
    FFPROBE,
    // video_readiness.py 프레임 샘플링 후 갱신
    FRAME_SAMPLE
}
