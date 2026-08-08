package com.example.demo.service.compare;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.CompareItemResult;
import com.example.demo.domain.enums.CompareVerdict;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.util.FfprobeCompareHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompareItemEvaluatorTest {

    @Mock
    private BlockchainAnchorService blockchainAnchorService;

    private CompareItemEvaluator evaluator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        evaluator = new CompareItemEvaluator(blockchainAnchorService, objectMapper);
    }

    @Test
    void buildComparisonItems_codecUsesFfprobeFormatNotMetadataCodec() {
        Evidence original = Evidence.builder()
                .fileName("test.mp4")
                .fileType(FileType.VIDEO)
                .fileSize(10477536L)
                .originalHashValue("abc")
                .build();
        ReflectionTestUtils.setField(original, "evidenceId", 156L);

        EvidenceMetadata metadata = new EvidenceMetadata();
        metadata.setCodec("h264");
        metadata.setFfprobeJson("""
                {
                  "format": { "duration": "106.0" },
                  "streams": [
                    { "codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720,
                      "avg_frame_rate": "24/1", "has_b_frames": 0, "pix_fmt": "yuv420p" },
                    { "codec_type": "audio", "codec_name": "aac" }
                  ]
                }
                """);

        Optional<FfprobeCompareHelper.ProbeSnapshot> candidateProbe =
                FfprobeCompareHelper.fromFfprobeJson(metadata.getFfprobeJson(), objectMapper);
        Optional<FfprobeCompareHelper.ProbeSnapshot> originalProbe =
                FfprobeCompareHelper.fromFfprobeJson(metadata.getFfprobeJson(), objectMapper);
        when(blockchainAnchorService.findAnchoredEvidenceSubjectHash(156L)).thenReturn(Optional.empty());

        List<CompareItemDto> items = evaluator.buildComparisonItems(
                original, metadata, "abc", 10477536L, originalProbe, candidateProbe);

        CompareItemDto codecItem = items.stream()
                .filter(item -> "CODEC".equals(item.getItemKey()))
                .findFirst()
                .orElseThrow();

        assertThat(codecItem.getOriginalValue()).isEqualTo("h264 / aac");
        assertThat(codecItem.getCandidateValue()).isEqualTo("h264 / aac");
        assertThat(codecItem.getResult()).isEqualTo(CompareItemResult.MATCH);
    }

    @Test
    void buildComparisonItems_codecMismatchWhenStoredProbeLacksAudioTrack() {
        Evidence original = Evidence.builder()
                .fileName("test.mp4")
                .fileType(FileType.VIDEO)
                .fileSize(10477536L)
                .originalHashValue("abc")
                .build();
        ReflectionTestUtils.setField(original, "evidenceId", 156L);

        EvidenceMetadata metadata = new EvidenceMetadata();
        metadata.setCodec("h264");
        metadata.setFfprobeJson("""
                {
                  "format": { "duration": "106.0" },
                  "streams": [
                    { "codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720,
                      "avg_frame_rate": "24/1", "has_b_frames": 0, "pix_fmt": "yuv420p" }
                  ]
                }
                """);

        Optional<FfprobeCompareHelper.ProbeSnapshot> storedProbe =
                FfprobeCompareHelper.fromFfprobeJson(metadata.getFfprobeJson(), objectMapper);
        Optional<FfprobeCompareHelper.ProbeSnapshot> candidateProbe =
                FfprobeCompareHelper.fromFfprobeJson("""
                {
                  "format": { "duration": "106.0" },
                  "streams": [
                    { "codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720,
                      "avg_frame_rate": "24/1", "has_b_frames": 0, "pix_fmt": "yuv420p" },
                    { "codec_type": "audio", "codec_name": "aac" }
                  ]
                }
                """, objectMapper);
        when(blockchainAnchorService.findAnchoredEvidenceSubjectHash(156L)).thenReturn(Optional.empty());

        List<CompareItemDto> items = evaluator.buildComparisonItems(
                original, metadata, "abc", 10477536L, storedProbe, candidateProbe);

        CompareItemDto codecItem = items.stream()
                .filter(item -> "CODEC".equals(item.getItemKey()))
                .findFirst()
                .orElseThrow();

        assertThat(codecItem.getOriginalValue()).isEqualTo("h264");
        assertThat(codecItem.getCandidateValue()).isEqualTo("h264 / aac");
        assertThat(codecItem.getResult()).isEqualTo(CompareItemResult.MISMATCH);
    }

    @Test
    void determineVerdict_matchingHashReturnsOriginalMatch() {
        CompareVerdict verdict = evaluator.determineVerdict(List.of(), "same-hash", "same-hash");

        assertThat(verdict).isEqualTo(CompareVerdict.ORIGINAL_MATCH);
    }

    @Test
    void determineVerdict_mismatchItemReturnsTampered() {
        List<CompareItemDto> items = List.of(
                CompareItemDto.builder()
                        .itemKey("FILE_SIZE")
                        .result(CompareItemResult.MISMATCH)
                        .build()
        );

        CompareVerdict verdict = evaluator.determineVerdict(items, "candidate", "original");

        assertThat(verdict).isEqualTo(CompareVerdict.TAMPERED);
    }
}
