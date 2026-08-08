package com.example.demo.service.evidence.hls;

import com.example.demo.config.HlsPackagingProperties;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.repository.EvidenceHlsRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.service.evidence.EvidenceStoragePaths;
import com.example.demo.util.JsonPayloadWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceHlsPackagingService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceHlsRepository evidenceHlsRepository;
    private final EvidenceHlsWorkFileService workFileService;
    private final EvidenceHlsContentKeyProtector contentKeyProtector;
    private final HlsPackagingProperties properties;
    private final CustodyLogService custodyLogService;
    private final JsonPayloadWriter jsonPayloadWriter;

    @Value("${ffmpeg.path:}")
    private String configuredFfmpegPath;

    private final SecureRandom secureRandom = new SecureRandom();

    public void packageEvidence(Long evidenceId) {
        if (!properties.isEnabled()) {
            return;
        }
        HlsStatus statusBeforeAttempt = evidenceHlsRepository.findByEvidenceId(evidenceId)
                .map(EvidenceHls::getHlsStatus)
                .orElse(HlsStatus.PENDING);
        boolean shouldRecordFailureAudit = statusBeforeAttempt != HlsStatus.FAILED;

        if (!tryMarkPackaging(evidenceId)) {
            log.debug("Skip HLS packaging evidenceId={} (already running or ready)", evidenceId);
            return;
        }

        Path workDir = null;
        try {
            Evidence evidence = evidenceRepository.findByEvidenceId(evidenceId)
                    .orElseThrow(() -> new IllegalStateException("Evidence not found: " + evidenceId));
            if (evidence.getFileType() != FileType.VIDEO) {
                markFailed(evidenceId, "HLS packaging is only supported for VIDEO evidence", true);
                if (shouldRecordFailureAudit) {
                    recordPackaged(evidence, false, "HLS packaging is only supported for VIDEO evidence");
                }
                return;
            }

            workDir = workFileService.prepareWorkDir(evidenceId);
            Path input = workFileService.downloadOriginal(evidence, workDir);

            byte[] contentKey = new byte[16];
            secureRandom.nextBytes(contentKey);
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);

            Path keyFile = workDir.resolve("enc.key");
            Files.write(keyFile, contentKey);

            String keyUri = "/api/v1/evidences/" + evidenceId + "/hls/key";
            Path keyInfo = workDir.resolve("keyinfo.txt");
            Files.writeString(
                    keyInfo,
                    keyUri + System.lineSeparator()
                            + keyFile.toAbsolutePath() + System.lineSeparator()
                            + HexFormat.of().formatHex(iv) + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            Path playlist = workDir.resolve("master.m3u8");
            runFfmpeg(input, workDir, keyInfo, playlist);

            String s3Prefix = EvidenceStoragePaths.hlsPrefix(evidenceId);
            workFileService.uploadPackagedFiles(workDir, s3Prefix);
            workFileService.purgeStrayUploadsUnderPrefix(s3Prefix);

            byte[] encryptedKey = contentKeyProtector.encrypt(contentKey);
            markReady(evidenceId, s3Prefix, encryptedKey);
            recordPackaged(evidence, true, null);
            log.info("HLS packaging completed evidenceId={} prefix={}", evidenceId, s3Prefix);
        } catch (Exception ex) {
            log.error("HLS packaging failed evidenceId={}", evidenceId, ex);
            boolean permanent = HlsPackagingFailureClassifier.isPermanent(ex);
            markFailed(evidenceId, truncate(ex.getMessage()), permanent);
            if (shouldRecordFailureAudit) {
                evidenceRepository.findByEvidenceId(evidenceId).ifPresent(evidence ->
                        recordPackaged(evidence, false, ex.getMessage()));
            }
        } finally {
            if (workDir != null) {
                workFileService.deleteDirectoryQuietly(workDir);
            }
        }
    }

    @Transactional
    public boolean tryMarkPackaging(Long evidenceId) {
        LocalDateTime now = LocalDateTime.now();
        EvidenceHls hls = evidenceHlsRepository.findByEvidenceId(evidenceId)
                .orElseGet(() -> EvidenceHls.createPending(evidenceId, now));
        if (hls.getHlsStatus() == HlsStatus.PACKAGING || hls.getHlsStatus() == HlsStatus.READY) {
            return false;
        }
        hls.markPackaging(now);
        evidenceHlsRepository.save(hls);
        return true;
    }

    @Transactional
    public void markReady(Long evidenceId, String storagePrefix, byte[] encryptedKey) {
        EvidenceHls hls = evidenceHlsRepository.findByEvidenceId(evidenceId)
                .orElseGet(() -> EvidenceHls.createPending(evidenceId, LocalDateTime.now()));
        hls.markReady(storagePrefix, encryptedKey, LocalDateTime.now());
        evidenceHlsRepository.save(hls);
    }

    @Transactional
    public void markFailed(Long evidenceId, String errorMessage) {
        markFailed(evidenceId, errorMessage, false);
    }

    @Transactional
    public void markFailed(Long evidenceId, String errorMessage, boolean permanent) {
        EvidenceHls hls = evidenceHlsRepository.findByEvidenceId(evidenceId)
                .orElseGet(() -> EvidenceHls.createPending(evidenceId, LocalDateTime.now()));
        hls.markFailed(HlsPackagingFailureClassifier.toStoredError(errorMessage, permanent), LocalDateTime.now());
        evidenceHlsRepository.save(hls);
    }

    @Transactional
    public int rollbackStalePackaging() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(properties.getStalePackagingMinutes());
        List<EvidenceHls> staleRows = evidenceHlsRepository.findStalePackagingRows(staleBefore);
        for (EvidenceHls row : staleRows) {
            row.rollbackStalePackaging(LocalDateTime.now());
            evidenceHlsRepository.save(row);
            log.warn("Rolled back stale HLS PACKAGING evidenceId={}", row.getEvidenceId());
        }
        return staleRows.size();
    }

    private void runFfmpeg(Path input, Path workDir, Path keyInfo, Path playlist) throws IOException, InterruptedException {
        String ffmpeg = resolveFfmpegPath();
        List<String> command = List.of(
                ffmpeg,
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                input.toAbsolutePath().toString(),
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "23",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-hls_time",
                String.valueOf(properties.getSegmentDurationSec()),
                "-hls_playlist_type",
                "vod",
                "-hls_key_info_file",
                keyInfo.toAbsolutePath().toString(),
                "-hls_segment_filename",
                workDir.resolve("seg_%03d.ts").toAbsolutePath().toString(),
                playlist.toAbsolutePath().toString()
        );

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        boolean finished = process.waitFor(properties.getTimeoutMinutes(), TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg timed out after " + properties.getTimeoutMinutes() + " minutes");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("ffmpeg failed (exit=" + process.exitValue() + "): " + output);
        }
        if (!Files.isRegularFile(playlist) || Files.size(playlist) == 0) {
            throw new IllegalStateException("ffmpeg did not produce master.m3u8");
        }
    }

    private String resolveFfmpegPath() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String executable = isWindows ? "ffmpeg.exe" : "ffmpeg";
        if (configuredFfmpegPath != null && !configuredFfmpegPath.isBlank()) {
            Path candidate = Paths.get(configuredFfmpegPath, executable);
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return executable;
    }

    private void recordPackaged(Evidence evidence, boolean success, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        payload.put("hlsPrefix", EvidenceStoragePaths.hlsPrefix(evidence.getEvidenceId()));
        if (error != null) {
            payload.put("error", truncate(error));
        }
        custodyLogService.record(
                evidence.getUploaderId(),
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "EVIDENCE_HLS_PACKAGED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                success ? "HLS 패키징 완료 · EVD-" + evidence.getEvidenceId() : "HLS 패키징 실패 · EVD-" + evidence.getEvidenceId(),
                jsonPayloadWriter.toJson(payload),
                null
        );
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
