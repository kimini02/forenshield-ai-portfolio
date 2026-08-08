package com.example.demo.service.compare;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.FileType;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.service.readiness.EvidenceReadinessFileService;
import com.example.demo.util.FfprobeCompareHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompareOriginalProbeExtractorTest {

    @Mock
    private EvidenceReadinessFileService evidenceReadinessFileService;

    @Mock
    private CompareCandidateFileHandler candidateFileHandler;

    @Mock
    private EvidenceMetadataRepository evidenceMetadataRepository;

    private CompareOriginalProbeExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new CompareOriginalProbeExtractor(
                evidenceReadinessFileService,
                candidateFileHandler,
                evidenceMetadataRepository,
                new ObjectMapper()
        );
    }

    @Test
    void extract_prefersLiveFfprobeFromS3Original() throws Exception {
        Evidence evidence = Evidence.builder()
                .fileName("clip.mp4")
                .fileType(FileType.VIDEO)
                .originalStoragePath("cases/test/156/original/test-156.mp4")
                .build();
        ReflectionTestUtils.setField(evidence, "evidenceId", 156L);

        Path tempPath = Path.of("clip.mp4");
        FfprobeCompareHelper.ProbeSnapshot liveProbe = FfprobeCompareHelper.ProbeSnapshot.builder()
                .videoCodec("h264")
                .audioCodec("aac")
                .build();

        when(evidenceReadinessFileService.downloadOriginal(evidence)).thenReturn(tempPath);
        when(candidateFileHandler.extractProbe(tempPath)).thenReturn(Optional.of(liveProbe));

        Optional<FfprobeCompareHelper.ProbeSnapshot> result = extractor.extract(evidence);

        assertThat(result).contains(liveProbe);
        verify(evidenceReadinessFileService).deleteQuietly(tempPath);
    }

    @Test
    void extract_fallsBackToStoredFfprobeJsonWhenDownloadFails() throws Exception {
        Evidence evidence = Evidence.builder()
                .fileName("clip.mp4")
                .fileType(FileType.VIDEO)
                .originalStoragePath("cases/test/156/original/test-156.mp4")
                .build();
        ReflectionTestUtils.setField(evidence, "evidenceId", 156L);

        EvidenceMetadata metadata = new EvidenceMetadata();
        metadata.setFfprobeJson("""
                {
                  "format": { "duration": "106.0" },
                  "streams": [
                    { "codec_type": "video", "codec_name": "h264" },
                    { "codec_type": "audio", "codec_name": "aac" }
                  ]
                }
                """);

        when(evidenceReadinessFileService.downloadOriginal(any())).thenThrow(new RuntimeException("s3 down"));
        when(evidenceMetadataRepository.findByEvidenceId(156L)).thenReturn(Optional.of(metadata));

        Optional<FfprobeCompareHelper.ProbeSnapshot> result = extractor.extract(evidence);

        assertThat(result)
                .map(FfprobeCompareHelper.ProbeSnapshot::getVideoCodec)
                .contains("h264");
        assertThat(result)
                .map(FfprobeCompareHelper.ProbeSnapshot::getAudioCodec)
                .contains("aac");
        verify(evidenceReadinessFileService).deleteQuietly(null);
    }
}
