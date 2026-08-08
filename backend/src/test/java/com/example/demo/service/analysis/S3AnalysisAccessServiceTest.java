package com.example.demo.service.analysis;

import com.example.demo.config.AnalysisMessagingProperties;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.exception.AnalysisDispatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3AnalysisAccessServiceTest {

    @Mock
    private S3Client s3Client;

    private S3AnalysisAccessService service;

    @BeforeEach
    void setUp() {
        AnalysisMessagingProperties properties = new AnalysisMessagingProperties();
        service = new S3AnalysisAccessService(s3Client, Optional.empty(), properties);
        ReflectionTestUtils.setField(service, "evidenceBucket", "test-evidence-bucket");
        ReflectionTestUtils.setField(service, "awsRegion", "ap-northeast-2");
    }

    @Test
    void createPresignedOriginalUrl_returnsNullForPendingPath() {
        assertThat(service.createPresignedOriginalUrl("pending")).isNull();
    }

    @Test
    void createGpuDownloadUrl_returnsS3UriWhenPresignerUnavailable() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        String url = service.createGpuDownloadUrl("cases/demo/1/copy/video.mp4");

        assertThat(url).isEqualTo("s3://test-evidence-bucket/cases/demo/1/copy/video.mp4");
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void assertCopyObjectExists_throwsWhenObjectMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThatThrownBy(() -> service.assertCopyObjectExists("missing/key"))
                .isInstanceOf(AnalysisDispatchException.class)
                .hasMessageContaining("사본을 찾을 수 없습니다");
    }
}
