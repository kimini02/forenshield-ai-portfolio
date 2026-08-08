package com.example.demo.service.analysis;

import com.example.demo.config.AnalysisMessagingProperties;
import com.example.demo.exception.AnalysisDispatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3AnalysisAccessService {

    private static final String S3_COPY_NOT_READY = "S3_COPY_NOT_READY";

    private final S3Client s3Client;
    private final Optional<S3Presigner> s3Presigner;
    private final AnalysisMessagingProperties messagingProperties;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    @Value("${aws.region}")
    private String awsRegion;

    public void assertCopyObjectExists(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new AnalysisDispatchException(S3_COPY_NOT_READY, "분석용 S3 사본 경로가 없습니다.");
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(evidenceBucket)
                    .key(objectKey)
                    .build());
        } catch (NoSuchKeyException ex) {
            throw new AnalysisDispatchException(
                    S3_COPY_NOT_READY,
                    "분석용 S3 사본을 찾을 수 없습니다.",
                    ex
            );
        } catch (S3Exception ex) {
            throw new AnalysisDispatchException(
                    S3_COPY_NOT_READY,
                    "분석용 S3 사본 확인에 실패했습니다.",
                    ex
            );
        }
    }

    public String createGpuDownloadUrl(String objectKey) {
        assertCopyObjectExists(objectKey);
        return createPresignedGetUrl(objectKey);
    }

    public String createPresignedOriginalUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || "pending".equalsIgnoreCase(objectKey)) {
            return null;
        }
        return createPresignedGetUrl(objectKey);
    }

    private String createPresignedGetUrl(String objectKey) {
        if (s3Presigner.isPresent()) {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(messagingProperties.getPresignDurationMinutes()))
                    .getObjectRequest(request -> request
                            .bucket(evidenceBucket)
                            .key(objectKey))
                    .build();
            PresignedGetObjectRequest presigned = s3Presigner.get().presignGetObject(presignRequest);
            return presigned.url().toString();
        }

        String s3Uri = "s3://" + evidenceBucket + "/" + objectKey;
        log.debug("S3 presigner unavailable; using S3 URI for media download reference: {}", s3Uri);
        return s3Uri;
    }

    public String getEvidenceBucket() {
        return evidenceBucket;
    }

    public String getAwsRegion() {
        return awsRegion;
    }
}
