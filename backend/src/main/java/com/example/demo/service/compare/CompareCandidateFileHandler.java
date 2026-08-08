package com.example.demo.service.compare;

import com.example.demo.dto.MediaMetadata;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.evidence.MediaService;
import com.example.demo.util.FfprobeCompareHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class CompareCandidateFileHandler {

    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public Path saveTempCandidate(MultipartFile candidateFile) {
        try {
            Path tempDir = Paths.get(uploadDir, "compare-temp");
            Files.createDirectories(tempDir);
            Path tempFile = tempDir.resolve(UUID.randomUUID() + "-" + candidateFile.getOriginalFilename());
            candidateFile.transferTo(tempFile);
            return tempFile;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COMPARE_FILE_ERROR", "비교 대상 파일 처리에 실패했습니다.");
        }
    }

    public void deleteQuietly(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    public Optional<FfprobeCompareHelper.ProbeSnapshot> extractProbe(Path tempFile) {
        try {
            MediaMetadata metadata = mediaService.extractMetadata(tempFile);
            if (metadata.getFfprobeJson() != null) {
                return FfprobeCompareHelper.fromFfprobeJson(metadata.getFfprobeJson(), objectMapper);
            }
        } catch (Exception ignored) {
            // ffprobe unavailable in test/dev — metadata items will be SKIPPED
        }
        return Optional.empty();
    }
}
