package com.example.demo.service.evidence.hls;

import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.EvidenceHlsRepository;
import com.example.demo.service.auth.StepUpAuthService;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.service.evidence.EvidenceStoragePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Service
@RequiredArgsConstructor
public class EvidenceHlsStreamService {

    private static final Pattern SEGMENT_FILE_PATTERN = Pattern.compile("seg_\\d{3}\\.ts");

    private final EvidenceAccessService evidenceAccessService;
    private final EvidenceHlsRepository evidenceHlsRepository;
    private final EvidenceHlsContentKeyProtector contentKeyProtector;
    private final EvidenceStreamTokenRedisService streamTokenRedisService;
    private final StepUpAuthService stepUpAuthService;
    private final S3Client s3Client;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    @Transactional(readOnly = true)
    public String loadMasterManifest(User user, Long evidenceId, String streamToken) {
        validateStreamToken(user, evidenceId, streamToken);
        EvidenceHls hls = requireReadyHls(evidenceId);
        String s3Key = resolveStorageKey(hls, EvidenceStoragePaths.hlsMasterKey(evidenceId));
        String raw = readTextObject(s3Key);
        return HlsManifestRewriter.rewrite(raw, evidenceId, streamToken);
    }

    public byte[] loadContentKey(User user, Long evidenceId, String streamToken, String stepUpToken) {
        validateStreamToken(user, evidenceId, streamToken);
        stepUpAuthService.requireValidStepUp(user, stepUpToken);
        EvidenceHls hls = requireReadyHls(evidenceId);
        if (hls.getContentKeyEnc() == null || hls.getContentKeyEnc().length == 0) {
            throw hlsNotReady();
        }
        return contentKeyProtector.decrypt(hls.getContentKeyEnc());
    }

    public byte[] loadSegment(User user, Long evidenceId, String fileName, String streamToken) {
        validateStreamToken(user, evidenceId, streamToken);
        validateSegmentFileName(fileName);
        EvidenceHls hls = requireReadyHls(evidenceId);
        String s3Key = resolveStorageKey(hls, hls.getHlsStoragePrefix() + fileName);
        return readBinaryObject(s3Key);
    }

    private void validateStreamToken(User user, Long evidenceId, String streamToken) {
        if (streamToken == null || streamToken.isBlank()) {
            throw streamTokenRequired();
        }
        EvidenceStreamTokenRedisService.StreamTokenContext context = streamTokenRedisService.resolve(streamToken)
                .orElseThrow(this::invalidStreamToken);
        if (!context.userId().equals(user.getUserId()) || !context.evidenceId().equals(evidenceId)) {
            throw invalidStreamToken();
        }
        evidenceAccessService.requireReadable(user, evidenceId);
    }

    private EvidenceHls requireReadyHls(Long evidenceId) {
        EvidenceHls hls = evidenceHlsRepository.findByEvidenceId(evidenceId)
                .orElseThrow(this::hlsNotReady);
        if (hls.getHlsStatus() != HlsStatus.READY) {
            throw hlsNotReady();
        }
        if (hls.getHlsStoragePrefix() == null || hls.getHlsStoragePrefix().isBlank()) {
            throw hlsNotReady();
        }
        return hls;
    }

    private String resolveStorageKey(EvidenceHls hls, String defaultKey) {
        String prefix = hls.getHlsStoragePrefix();
        if (prefix != null && !prefix.isBlank()) {
            String fileName = defaultKey.substring(defaultKey.lastIndexOf('/') + 1);
            return prefix.endsWith("/") ? prefix + fileName : prefix + "/" + fileName;
        }
        return defaultKey;
    }

    private void validateSegmentFileName(String fileName) {
        if (fileName == null || !SEGMENT_FILE_PATTERN.matcher(fileName).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_HLS_SEGMENT", "유효하지 않은 HLS 세그먼트입니다.");
        }
    }

    private String readTextObject(String s3Key) {
        byte[] bytes = readBinaryObject(s3Key);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] readBinaryObject(String s3Key) {
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(evidenceBucket)
                        .key(s3Key)
                        .build())) {
            return stream.readAllBytes();
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "HLS_OBJECT_NOT_FOUND", "HLS 객체를 찾을 수 없습니다.");
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "HLS_READ_FAILED", "HLS 객체 읽기에 실패했습니다.");
        }
    }

    private static BusinessException streamTokenRequired() {
        return new BusinessException(
                HttpStatus.FORBIDDEN,
                "STREAM_TOKEN_REQUIRED",
                "HLS 재생을 위해 stream token이 필요합니다."
        );
    }

    private BusinessException invalidStreamToken() {
        return new BusinessException(
                HttpStatus.FORBIDDEN,
                "STREAM_TOKEN_INVALID",
                "stream token이 유효하지 않거나 만료되었습니다."
        );
    }

    private BusinessException hlsNotReady() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "HLS_NOT_READY",
                "HLS 재생 준비가 완료되지 않았습니다."
        );
    }
}
