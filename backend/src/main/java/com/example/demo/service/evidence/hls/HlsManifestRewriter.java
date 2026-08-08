package com.example.demo.service.evidence.hls;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HlsManifestRewriter {

    private static final Pattern KEY_URI_PATTERN = Pattern.compile("URI=\"([^\"]+)\"");

    private HlsManifestRewriter() {
    }

    static String rewrite(String rawManifest, Long evidenceId, String streamToken) {
        String encodedToken = URLEncoder.encode(streamToken, StandardCharsets.UTF_8);
        String query = "?streamToken=" + encodedToken;
        String keyPath = "/api/v1/evidences/" + evidenceId + "/hls/key" + query;
        String segmentBase = "/api/v1/evidences/" + evidenceId + "/hls/segments/";

        StringBuilder rewritten = new StringBuilder();
        for (String line : rawManifest.split("\\R", -1)) {
            if (line.startsWith("#EXT-X-KEY:")) {
                rewritten.append(rewriteKeyLine(line, keyPath)).append('\n');
            } else if (!line.startsWith("#") && !line.isBlank()) {
                String segmentFile = line.trim();
                rewritten.append(segmentBase).append(segmentFile).append(query).append('\n');
            } else {
                rewritten.append(line).append('\n');
            }
        }
        return rewritten.toString();
    }

    private static String rewriteKeyLine(String line, String keyPath) {
        Matcher matcher = KEY_URI_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.replaceFirst("URI=\"" + Matcher.quoteReplacement(keyPath) + "\"");
        }
        return line;
    }
}
