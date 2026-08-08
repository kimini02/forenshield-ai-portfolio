package com.example.demo.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

public final class FfprobeCompareHelper {

    private FfprobeCompareHelper() {
    }

    @Getter
    @Builder
    public static class ProbeSnapshot {
        private final Integer durationSec;
        private final String videoCodec;
        private final String audioCodec;
        private final String timestamp;
        private final String gopFingerprint;
        private final String streamFingerprint;
    }

    public static Optional<ProbeSnapshot> fromFfprobeJson(String ffprobeJson, ObjectMapper objectMapper) {
        if (ffprobeJson == null || ffprobeJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(ffprobeJson);
            JsonNode format = root.path("format");
            JsonNode streams = root.path("streams");

            Integer durationSec = null;
            if (format.hasNonNull("duration")) {
                durationSec = (int) Math.round(format.get("duration").asDouble());
            }

            String videoCodec = null;
            String audioCodec = null;
            String gopParts = "";
            String streamParts = "";

            for (JsonNode stream : streams) {
                String codecType = stream.path("codec_type").asText("");
                if ("video".equals(codecType)) {
                    videoCodec = stream.path("codec_name").asText(null);
                    gopParts = stream.path("codec_name").asText("")
                            + "|" + stream.path("width").asText("")
                            + "|" + stream.path("height").asText("")
                            + "|" + stream.path("avg_frame_rate").asText("")
                            + "|" + stream.path("has_b_frames").asText("");
                    streamParts = stream.path("codec_name").asText("")
                            + "|" + stream.path("pix_fmt").asText("")
                            + "|" + stream.path("bit_rate").asText("")
                            + "|" + stream.path("nb_frames").asText("");
                } else if ("audio".equals(codecType) && audioCodec == null) {
                    audioCodec = stream.path("codec_name").asText(null);
                }
            }

            String timestamp = null;
            JsonNode tags = format.path("tags");
            if (tags.hasNonNull("creation_time")) {
                timestamp = tags.get("creation_time").asText();
            } else if (tags.hasNonNull("date")) {
                timestamp = tags.get("date").asText();
            }

            return Optional.of(ProbeSnapshot.builder()
                    .durationSec(durationSec)
                    .videoCodec(videoCodec)
                    .audioCodec(audioCodec)
                    .timestamp(timestamp)
                    .gopFingerprint(gopParts.isBlank() ? null : gopParts)
                    .streamFingerprint(streamParts.isBlank() ? null : streamParts)
                    .build());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public static String formatCodec(String videoCodec, String audioCodec) {
        if (videoCodec == null && audioCodec == null) {
            return null;
        }
        if (audioCodec == null) {
            return videoCodec;
        }
        if (videoCodec == null) {
            return audioCodec;
        }
        return videoCodec + " / " + audioCodec;
    }
}
