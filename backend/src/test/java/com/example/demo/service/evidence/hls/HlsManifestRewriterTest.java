package com.example.demo.service.evidence.hls;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HlsManifestRewriterTest {

    @Test
    void rewrite_replacesKeyUriAndSegmentPaths() {
        String raw = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:6
                #EXT-X-KEY:METHOD=AES-128,URI="/api/v1/evidences/9/hls/key",IV=0xabc
                #EXTINF:6.000000,
                seg_000.ts
                #EXT-X-ENDLIST
                """;

        String rewritten = HlsManifestRewriter.rewrite(raw, 9L, "token-123");

        assertThat(rewritten).contains("/api/v1/evidences/9/hls/key?streamToken=token-123");
        assertThat(rewritten).contains("/api/v1/evidences/9/hls/segments/seg_000.ts?streamToken=token-123");
        assertThat(rewritten).doesNotContain("\nseg_000.ts\n");
    }
}
