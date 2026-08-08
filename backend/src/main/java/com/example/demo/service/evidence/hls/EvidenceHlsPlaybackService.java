package com.example.demo.service.evidence.hls;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.dto.detail.HlsPlaybackDto;
import com.example.demo.repository.EvidenceHlsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceHlsPlaybackService {

    private final EvidenceHlsRepository evidenceHlsRepository;
    private final EvidenceStreamTokenRedisService streamTokenRedisService;

    public HlsPlaybackDto buildForDetail(Long userId, Evidence evidence) {
        if (evidence.getFileType() != FileType.VIDEO) {
            return null;
        }

        Long evidenceId = evidence.getEvidenceId();
        HlsStatus status = evidenceHlsRepository.findByEvidenceId(evidenceId)
                .map(EvidenceHls::getHlsStatus)
                .orElse(HlsStatus.PENDING);
        String streamToken = streamTokenRedisService.issueToken(userId, evidenceId);

        return HlsPlaybackDto.builder()
                .manifestPath("/api/v1/evidences/" + evidenceId + "/hls/master.m3u8")
                .hlsStatus(status.name())
                .streamToken(streamToken)
                .expiresIn(streamTokenRedisService.resolveExpiresInSeconds())
                .build();
    }
}
