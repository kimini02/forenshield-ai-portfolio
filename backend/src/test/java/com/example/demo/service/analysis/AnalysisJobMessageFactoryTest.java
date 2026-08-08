package com.example.demo.service.analysis;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.dto.FrameAnalysisSpecDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisJobMessageFactoryTest {

    @Mock
    private S3AnalysisAccessService s3AnalysisAccessService;

    @Mock
    private VideoFrameExtractionService videoFrameExtractionService;

    @InjectMocks
    private AnalysisJobMessageFactory factory;

    @Test
    void buildForGpuDispatch_includesS3AndGpuDownloadFields() {
        Evidence evidence = Evidence.builder()
                .uploaderId(1L)
                .caseName("테스트 사건")
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc123")
                .originalStoragePath("cases/test/1/original/test-case.mp4")
                .uploadedAt(LocalDateTime.now())
                .build();
        evidence.activateCopy("cases/test/1/copy/test-case.mp4", "abc123");
        ReflectionTestUtils.setField(evidence, "evidenceId", 1L);

        AnalysisRequest request = new AnalysisRequest();
        request.setAnalysisRequestId(10L);
        request.setRequestedAt(LocalDateTime.of(2026, 6, 17, 10, 0));

        when(s3AnalysisAccessService.createGpuDownloadUrl("cases/test/1/copy/test-case.mp4"))
                .thenReturn("https://s3.example.com/presigned");
        when(s3AnalysisAccessService.getEvidenceBucket()).thenReturn("forenshield-evidence");
        when(s3AnalysisAccessService.getAwsRegion()).thenReturn("ap-northeast-2");
        when(videoFrameExtractionService.buildSpecForDuration(0.0)).thenReturn(
                FrameAnalysisSpecDto.builder()
                        .extractionIntervalSec(1.0)
                        .highRiskFrameScoreThreshold(0.70)
                        .minSuspiciousSegmentSec(0.5)
                        .pixelFormat("RGB24")
                        .imageEncoding("jpeg")
                        .sampleTimestampsSec(List.of(0.0))
                        .build()
        );

        AnalysisJobMessage message = factory.buildForGpuDispatch(evidence, request, "테스트 사건");

        assertThat(message.getAnalysisRequestId()).isEqualTo(10L);
        assertThat(message.getEvidenceId()).isEqualTo(1L);
        assertThat(message.getFilePath()).isEqualTo("cases/test/1/copy/test-case.mp4");
        assertThat(message.getS3ObjectKey()).isEqualTo("cases/test/1/copy/test-case.mp4");
        assertThat(message.getS3Bucket()).isEqualTo("forenshield-evidence");
        assertThat(message.getS3Region()).isEqualTo("ap-northeast-2");
        assertThat(message.getPresignedDownloadUrl()).isEqualTo("https://s3.example.com/presigned");
        assertThat(message.getOriginalHash()).isEqualTo("abc123");
        assertThat(message.getOriginalSha256()).isEqualTo("abc123");
        assertThat(message.getFileType()).isEqualTo("video");
        assertThat(message.getFrameAnalysis()).isNotNull();
        assertThat(message.getFrameAnalysis().getPixelFormat()).isEqualTo("RGB24");
    }
}
