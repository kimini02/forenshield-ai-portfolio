package com.example.demo.service.readiness;

import com.example.demo.config.ReadinessProperties;
import com.example.demo.dto.readiness.ReadinessFrameMetricsDto;
import com.example.demo.dto.readiness.ReadinessMetricAggregateDto;
import com.example.demo.dto.readiness.ReadinessSnapshot;
import com.example.demo.dto.readiness.ReadinessSpatialDto;
import com.example.demo.dto.readiness.ReadinessVideoMetadataDto;
import com.example.demo.domain.enums.ReadinessSource;
import com.example.demo.domain.enums.ReadinessTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ai-forensic video_readiness.py 를 subprocess 로 호출해 프레임 샘플링 결과를 받는다.
 */

// video_readiness.py 실행 + JSON 파싱
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoReadinessRunner {

    private final ReadinessProperties properties;
    private final ObjectMapper objectMapper;

    public ReadinessSnapshot run(Path localVideoPath) {
        if (!properties.isFrameSamplingConfigured()) {
            throw new IllegalStateException("Frame readiness script is not configured (readiness.script-path)");
        }

        Path scriptPath = Path.of(properties.getScriptPath()).toAbsolutePath().normalize();
        if (!scriptPath.toFile().exists()) {
            throw new IllegalStateException("Readiness script not found: " + scriptPath);
        }

        List<String> command = List.of(
                properties.getPythonExecutable(),
                scriptPath.toString(),
                localVideoPath.toAbsolutePath().toString(),
                "--sample-every",
                String.valueOf(Math.max(1, properties.getSampleEvery())),
                "--no-frame-samples"
        );

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(properties.getProcessTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("video_readiness.py timed out after "
                        + properties.getProcessTimeoutSeconds() + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException("video_readiness.py exited with code " + exitCode
                        + ". output=" + truncate(output.toString()));
            }

            JsonNode root = objectMapper.readTree(output.toString());
            return mapPythonOutput(root);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to run video readiness script: " + ex.getMessage(), ex);
        }
    }

    private ReadinessSnapshot mapPythonOutput(JsonNode root) {
        ReadinessTier tier = ReadinessTier.valueOf(root.path("readinessTier").asText("GOOD"));
        List<String> reasons = new ArrayList<>();
        JsonNode reasonsNode = root.path("reasons");
        if (reasonsNode.isArray()) {
            reasonsNode.forEach(node -> reasons.add(node.asText()));
        }

        ReadinessVideoMetadataDto videoMetadata = null;
        JsonNode vm = root.path("videoMetadata");
        if (!vm.isMissingNode()) {
            videoMetadata = ReadinessVideoMetadataDto.builder()
                    .width(asNullableInt(vm.path("width")))
                    .height(asNullableInt(vm.path("height")))
                    .fps(asNullableDouble(vm.path("fps")))
                    .durationSec(asNullableInt(vm.path("durationSec")))
                    .totalFrames(asNullableInt(vm.path("totalFrames")))
                    .sampledFrames(asNullableInt(vm.path("sampledFrames")))
                    .sampleEvery(asNullableInt(vm.path("sampleEvery")))
                    .build();
        }

        ReadinessFrameMetricsDto frameMetrics = null;
        JsonNode fm = root.path("frameMetrics");
        if (!fm.isMissingNode() && !fm.isNull()) {
            frameMetrics = ReadinessFrameMetricsDto.builder()
                    .blur(mapAggregate(fm.path("blur")))
                    .blockiness(mapAggregate(fm.path("blockiness")))
                    .fftPeak(mapAggregate(fm.path("fftPeak")))
                    .build();
        }

        ReadinessSpatialDto spatial = null;
        JsonNode sp = root.path("spatial");
        if (!sp.isMissingNode()) {
            spatial = ReadinessSpatialDto.builder()
                    .worstRegion(emptyToNull(sp.path("worstRegion").asText(null)))
                    .worstRegionScore(asNullableDouble(sp.path("worstRegionScore")))
                    .spatiallyUniform(sp.path("isSpatiallyUniform").isBoolean()
                            ? sp.path("isSpatiallyUniform").asBoolean() : null)
                    .build();
        }

        return ReadinessSnapshot.builder()
                .source(ReadinessSource.FRAME_SAMPLE)
                .checkedAt(LocalDateTime.now())
                .readinessTier(tier)
                .confidenceCap(root.path("confidenceCap").asInt(100))
                .reasons(reasons)
                .requiresAcknowledgement(root.path("requiresAcknowledgement").asBoolean(false))
                .thresholdsVersion(root.path("thresholdsVersion").asText("notebook-ui-v1"))
                .videoMetadata(videoMetadata)
                .frameMetrics(frameMetrics)
                .spatial(spatial)
                .frameCheckStatus("ok".equals(root.path("status").asText()) ? "COMPLETED" : "FAILED")
                .frameCheckMessage(root.path("error").isNull() ? null : root.path("error").asText(null))
                .build();
    }

    private ReadinessMetricAggregateDto mapAggregate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return ReadinessMetricAggregateDto.builder()
                .mean(asNullableDouble(node.path("mean")))
                .min(asNullableDouble(node.path("min")))
                .max(asNullableDouble(node.path("max")))
                .build();
    }

    private Integer asNullableInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private Double asNullableDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asDouble();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
