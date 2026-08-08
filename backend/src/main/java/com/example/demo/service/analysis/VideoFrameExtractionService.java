package com.example.demo.service.analysis;

import com.example.demo.service.evidence.MediaService;
import com.example.demo.config.VideoFrameAnalysisProperties;
import com.example.demo.dto.FrameAnalysisSpecDto;
import com.example.demo.dto.MediaMetadata;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** SK-668~672, SK-670: FFmpeg/ffprobe 기반 프레임 샘플링·모델 입력 스펙 */
@Slf4j
@Service
public class VideoFrameExtractionService {

    private final MediaService mediaService;
    private final VideoFrameAnalysisProperties properties;
    private final String ffmpegPath;

    public VideoFrameExtractionService(
            MediaService mediaService,
            VideoFrameAnalysisProperties properties,
            @Value("${ffmpeg.path:}") String configuredFfmpegPath
    ) {
        this.mediaService = mediaService;
        this.properties = properties;
        this.ffmpegPath = resolveFfmpegPath(configuredFfmpegPath);
        log.info("ffmpeg path resolved to: {}", ffmpegPath);
    }

    public FrameAnalysisSpecDto buildSpecForDuration(double durationSec) {
        return FrameAnalysisSpecDto.builder()
                .extractionIntervalSec(properties.getExtractionIntervalSec())
                .highRiskFrameScoreThreshold(properties.getHighRiskFrameScoreThreshold())
                .minSuspiciousSegmentSec(properties.getMinSuspiciousSegmentSec())
                .pixelFormat(properties.getPixelFormat())
                .imageEncoding(properties.getImageEncoding())
                .sampleTimestampsSec(buildSampleTimestamps(durationSec))
                .build();
    }

    public FrameAnalysisSpecDto buildSpecForLocalVideo(Path videoPath) {
        double durationSec = 0.0;
        if (videoPath != null && Files.isRegularFile(videoPath)) {
            try {
                MediaMetadata metadata = mediaService.extractMetadata(videoPath);
                if (metadata.getDuration() != null) {
                    durationSec = metadata.getDuration();
                }
                verifyFfmpegFrameExtraction(videoPath);
            } catch (Exception ex) {
                log.warn("Frame extraction planning failed for {}: {}", videoPath, ex.getMessage());
            }
        }
        return buildSpecForDuration(durationSec);
    }

    /** SK-668: 일정 간격으로 샘플링 타임스탬프 생성 */
    public List<Double> buildSampleTimestamps(double durationSec) {
        if (durationSec <= 0) {
            return List.of(0.0);
        }

        List<Double> timestamps = new ArrayList<>();
        double interval = properties.getExtractionIntervalSec();
        for (double time = 0.0; time < durationSec; time += interval) {
            timestamps.add(round3(time));
            if (timestamps.size() >= properties.getMaxSampleTimestamps()) {
                break;
            }
        }
        if (timestamps.isEmpty()) {
            timestamps.add(0.0);
        }
        return timestamps;
    }

    /** SK-670: FFmpeg fps 필터로 1프레임 추출 가능 여부 확인 */
    public boolean verifyFfmpegFrameExtraction(Path videoPath) {
        try {
            List<String> command = List.of(
                    ffmpegPath,
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    videoPath.toAbsolutePath().toString(),
                    "-vf",
                    "fps=1/" + properties.getExtractionIntervalSec(),
                    "-vframes",
                    "1",
                    "-f",
                    "null",
                    "-"
            );
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("ffmpeg frame extraction verify failed (exit={}): {}", exitCode, output);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("ffmpeg frame extraction verify skipped: {}", ex.getMessage());
            return false;
        }
    }

    private String resolveFfmpegPath(String ffmpegPath) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String executable = isWindows ? "ffmpeg.exe" : "ffmpeg";

        if (ffmpegPath != null && !ffmpegPath.isBlank()) {
            Path candidate = Paths.get(ffmpegPath, executable);
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
            log.warn("Configured ffmpeg.path does not contain {} ({}). Falling back to system PATH.", executable, candidate);
        }
        return executable;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
