package com.example.demo.service.evidence;

import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.dto.MediaMetadata;
import com.example.demo.repository.EvidenceMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EvidenceMetadataService {

    private final EvidenceMetadataRepository evidenceMetadataRepository;

    public EvidenceMetadata saveFromExtraction(Long evidenceId, MediaMetadata media, ExtractionStatus status, String error) {
        EvidenceMetadata entity = new EvidenceMetadata();
        entity.setEvidenceId(evidenceId);
        entity.setExtractionStatus(status);
        entity.setExtractionError(error);
        entity.setCreatedAt(LocalDateTime.now());

        if (media != null) {
            if (media.getWidth() != null) {
                entity.setWidth(media.getWidth());
            }
            if (media.getHeight() != null) {
                entity.setHeight(media.getHeight());
            }
            if (media.getDuration() != null) {
                entity.setDurationSec((int) Math.round(media.getDuration()));
            }
            entity.setFps(media.getFps());
            entity.setCodec(media.getCodec());
            entity.setSampleRate(media.getSampleRate());
            entity.setChannels(media.getChannels());
            entity.setFfprobeJson(media.getFfprobeJson());
        }

        return evidenceMetadataRepository.save(entity);
    }
}
