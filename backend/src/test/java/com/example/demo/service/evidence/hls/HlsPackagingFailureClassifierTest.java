package com.example.demo.service.evidence.hls;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class HlsPackagingFailureClassifierTest {

    @Test
    void isPermanent_detectsNoSuchKey() {
        assertThat(HlsPackagingFailureClassifier.isPermanent(
                NoSuchKeyException.builder().message("The specified key does not exist.").build()
        )).isTrue();
    }

    @Test
    void isPermanent_detectsFfmpegOpenFailure() {
        assertThat(HlsPackagingFailureClassifier.isPermanent(
                new IllegalStateException("ffmpeg failed: Error opening input file /tmp/BUG.mp4")
        )).isTrue();
    }

    @Test
    void isPermanent_transientTimeoutIsRetryable() {
        assertThat(HlsPackagingFailureClassifier.isPermanent(
                new IllegalStateException("ffmpeg timed out after 30 minutes")
        )).isFalse();
    }

    @Test
    void toStoredError_prefixesPermanentFailures() {
        assertThat(HlsPackagingFailureClassifier.toStoredError("missing original", true))
                .startsWith(HlsPackagingFailureClassifier.PERMANENT_PREFIX);
        assertThat(HlsPackagingFailureClassifier.toStoredError("temporary", false))
                .isEqualTo("temporary");
    }

    @Test
    void isPermanentStoredError_matchesPrefix() {
        assertThat(HlsPackagingFailureClassifier.isPermanentStoredError("PERMANENT: missing"))
                .isTrue();
        assertThat(HlsPackagingFailureClassifier.isPermanentStoredError("ffmpeg timed out"))
                .isFalse();
    }
}
