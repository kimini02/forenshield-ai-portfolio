package com.example.demo.service.readiness;

import com.example.demo.domain.Evidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;


//  readiness 프레임 분석용으로 S3 원본을 임시 로컬 파일로 내려받는다.

// S3 원본 → 로컬 임시 파일
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceReadinessFileService {

    private final S3Client s3Client;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    @Value("${file.upload-dir:uploads/original}")
    private String uploadDir;

    public Path downloadOriginal(Evidence evidence) throws IOException {
        Path workRoot = Paths.get(uploadDir).resolve("readiness").resolve(String.valueOf(evidence.getEvidenceId()));
        Files.createDirectories(workRoot);
        cleanupOldFiles(workRoot);

        Path target = workRoot.resolve(sanitizeFileName(evidence.getFileName()));
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(evidenceBucket)
                        .key(evidence.getOriginalStoragePath())
                        .build())) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.debug("Downloaded evidence {} for readiness to {}", evidence.getEvidenceId(), target);
        return target;
    }

    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var stream = Files.list(parent)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Failed to delete readiness temp file {}", path, ex);
        }
    }

    private void cleanupOldFiles(Path workRoot) throws IOException {
        if (!Files.exists(workRoot)) {
            return;
        }
        try (var paths = Files.walk(workRoot)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(workRoot))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            log.warn("Failed to cleanup {}", path, ex);
                        }
                    });
        }
    }

    private String sanitizeFileName(String fileName) {
        String safe = Paths.get(fileName).getFileName().toString();
        return safe.isBlank() ? "evidence.bin" : safe;
    }
}
