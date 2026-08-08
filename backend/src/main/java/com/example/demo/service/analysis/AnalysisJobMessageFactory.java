package com.example.demo.service.analysis;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.dto.FrameAnalysisSpecDto;
import com.example.demo.util.ApiDateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisJobMessageFactory {

    private final S3AnalysisAccessService s3AnalysisAccessService;
    private final VideoFrameExtractionService videoFrameExtractionService;

    @Value("${file.upload-dir:uploads/original}")
    private String uploadDir;

    public AnalysisJobMessage buildForGpuDispatch(
            Evidence evidence,
            AnalysisRequest analysisRequest,
            String caseName
    ) {
        String copyKey = evidence.getCopyStoragePath();
        String presignedDownloadUrl = s3AnalysisAccessService.createGpuDownloadUrl(copyKey);
        FrameAnalysisSpecDto frameAnalysis = resolveFrameAnalysis(evidence);

        return AnalysisJobMessage.builder()
                .analysisRequestId(analysisRequest.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .fileType("video")
                .filePath(copyKey)
                .s3ObjectKey(copyKey)
                .s3Bucket(s3AnalysisAccessService.getEvidenceBucket())
                .s3Region(s3AnalysisAccessService.getAwsRegion())
                .presignedDownloadUrl(presignedDownloadUrl)
                .originalHash(evidence.getOriginalHashValue())
                .originalSha256(evidence.getOriginalHashValue())
                .caseName(caseName)
                .requestedAt(ApiDateTimeFormatter.formatUtc(analysisRequest.getRequestedAt()))
                .frameAnalysis(frameAnalysis)
                .build();
    }

    private FrameAnalysisSpecDto resolveFrameAnalysis(Evidence evidence) {
        Optional<Path> localPath = resolveLocalVideoPath(evidence);
        if (localPath.isPresent()) {
            return videoFrameExtractionService.buildSpecForLocalVideo(localPath.get());
        }
        return videoFrameExtractionService.buildSpecForDuration(0.0);
    }

    private Optional<Path> resolveLocalVideoPath(Evidence evidence) {
        String storedPath = evidence.getOriginalStoragePath();
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }

        Path direct = Paths.get(storedPath);
        if (Files.isRegularFile(direct)) {
            return Optional.of(direct);
        }

        String baseUploadDir = uploadDir == null || uploadDir.isBlank() ? "uploads/original" : uploadDir;
        Path underUpload = Paths.get(baseUploadDir).resolve(storedPath);
        if (Files.isRegularFile(underUpload)) {
            return Optional.of(underUpload);
        }

        Path fileNameOnly = Paths.get(baseUploadDir).resolve(Paths.get(storedPath).getFileName().toString());
        if (Files.isRegularFile(fileNameOnly)) {
            return Optional.of(fileNameOnly);
        }

        return Optional.empty();
    }
}
