package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "readiness")
public class ReadinessProperties {

    /** 프레임 샘플링(Python) 활성화. script-path 미설정 시 자동 비활성 */
    private boolean frameCheckEnabled = true;

    private String pythonExecutable = "python";

    /**
     * ai-forensic/scripts/profile/video_readiness.py 절대 또는 상대 경로.
     * 비어 있으면 FRAME_SAMPLE 미실행.
     */
    private String scriptPath = "";

    private int sampleEvery = 10;

    private int processTimeoutSeconds = 180;

    /** S3 다운로드·임시 영상 작업 디렉터리 */
    private String workDir = "";

    public boolean isFrameSamplingConfigured() {
        return frameCheckEnabled && scriptPath != null && !scriptPath.isBlank();
    }

    /** script-path 가 설정됐을 때 실제 파일이 이미지/볼륨에 존재하는지 */
    public boolean isScriptPresent() {
        if (!isFrameSamplingConfigured()) {
            return false;
        }
        return java.nio.file.Path.of(scriptPath).toFile().exists();
    }
}
