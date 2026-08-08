package com.example.demo.service.evidence;

import com.example.demo.dto.MediaMetadata;
import com.example.demo.exception.InvalidMediaFileException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MediaService {

    private final String ffprobePath;
    private final ObjectMapper objectMapper;

    public MediaService(@Value("${ffmpeg.path:}") String ffmpegPath, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.ffprobePath = resolveFfprobePath(ffmpegPath);
        log.info("ffprobe path resolved to: {}", this.ffprobePath);
    }

    /**
     * OS에 맞춰 ffprobe 실행 경로를 결정한다.
     * - ffmpeg.path가 지정되고 그 안에 실행 파일이 실제로 있으면 절대 경로 사용 (Windows 설치 폴더 등)
     * - 그렇지 않으면 실행 파일 이름만 반환해 시스템 PATH에서 찾도록 함 (Mac/Linux Homebrew 등)
     */
    private String resolveFfprobePath(String ffmpegPath) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String executable = isWindows ? "ffprobe.exe" : "ffprobe";

        if (ffmpegPath != null && !ffmpegPath.isBlank()) {
            Path candidate = Paths.get(ffmpegPath, executable);
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
            log.warn("Configured ffmpeg.path does not contain {} ({}). Falling back to system PATH.", executable, candidate);
        }
        return executable;
    }

    public MediaMetadata extractMetadata(Path filePath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(ffprobePath);
            command.add("-v");
            command.add("quiet");
            command.add("-print_format");
            command.add("json");
            command.add("-show_format");
            command.add("-show_streams");
            command.add(filePath.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("ffprobe failed with exit code: {}", exitCode);
                throw new InvalidMediaFileException();
            }

            JsonNode rootNode = objectMapper.readTree(output.toString());
            return parseMetadata(rootNode, output.toString());

        } catch (InvalidMediaFileException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract metadata: ", e);
            throw new InvalidMediaFileException(e);
        }
    }

    private MediaMetadata parseMetadata(JsonNode rootNode, String ffprobeJson) {
        JsonNode formatNode = rootNode.get("format");
        JsonNode streamsNode = rootNode.get("streams");

        MediaMetadata.MediaMetadataBuilder builder = MediaMetadata.builder()
                .type("video")
                .ffprobeJson(ffprobeJson);

        if (formatNode != null && formatNode.has("duration")) {
            builder.duration(formatNode.get("duration").asDouble());
        }

        boolean hasVideo = false;
        boolean hasAudio = false;

        if (streamsNode != null && streamsNode.isArray()) {
            for (JsonNode stream : streamsNode) {
                String codecType = stream.get("codec_type").asText();
                if ("video".equals(codecType) && !hasVideo) {
                    hasVideo = true;
                    builder.codec(stream.get("codec_name").asText());
                    if (stream.has("width")) {
                        builder.width(stream.get("width").asInt());
                    }
                    if (stream.has("height")) {
                        builder.height(stream.get("height").asInt());
                    }

                    if (stream.has("avg_frame_rate")) {
                        String avgFrameRate = stream.get("avg_frame_rate").asText();
                        if (avgFrameRate.contains("/")) {
                            String[] parts = avgFrameRate.split("/");
                            try {
                                double num = Double.parseDouble(parts[0]);
                                double den = Double.parseDouble(parts[1]);
                                builder.fps(den != 0 ? num / den : 0);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                } else if ("audio".equals(codecType) && !hasAudio) {
                    // 영상 파일 내장 오디오 트랙 메타 (별도 음성 파일 분석 아님)
                    hasAudio = true;
                    if (stream.has("sample_rate")) {
                        builder.sampleRate(stream.get("sample_rate").asInt());
                    }
                    if (stream.has("channels")) {
                        builder.channels(stream.get("channels").asInt());
                    }
                }
            }
        }

        builder.hasAudioTrack(hasAudio);
        return builder.build();
    }
}
