package com.example.demo.service.compare;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.service.readiness.EvidenceReadinessFileService;
import com.example.demo.util.FfprobeCompareHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Compare verification uses live ffprobe on the S3 original so codec labels match the candidate file.
 * Falls back to stored {@code ffprobe_json} when download or probe fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareOriginalProbeExtractor {

    private final EvidenceReadinessFileService evidenceReadinessFileService;
    private final CompareCandidateFileHandler candidateFileHandler;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final ObjectMapper objectMapper;

    public Optional<FfprobeCompareHelper.ProbeSnapshot> extract(Evidence evidence) {
        Path tempFile = null;
        try {
            tempFile = evidenceReadinessFileService.downloadOriginal(evidence);
            Optional<FfprobeCompareHelper.ProbeSnapshot> liveProbe = candidateFileHandler.extractProbe(tempFile);
            if (liveProbe.isPresent()) {
                return liveProbe;
            }
        } catch (Exception ex) {
            log.warn("Live ffprobe on original evidence {} failed, falling back to stored metadata: {}",
                    evidence.getEvidenceId(), ex.getMessage());
        } finally {
            evidenceReadinessFileService.deleteQuietly(tempFile);
        }

        return evidenceMetadataRepository.findByEvidenceId(evidence.getEvidenceId())
                .flatMap(this::fromStoredMetadata);
    }

    private Optional<FfprobeCompareHelper.ProbeSnapshot> fromStoredMetadata(EvidenceMetadata metadata) {
        return FfprobeCompareHelper.fromFfprobeJson(metadata.getFfprobeJson(), objectMapper);
    }
}
