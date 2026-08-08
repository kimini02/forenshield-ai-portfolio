package com.example.demo.service.evidence.hls;

import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.repository.EvidenceHlsRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HLS 메타 읽기 전용 조회. case detail 등 N건 조회 시 {@link #findByEvidenceIds}로 N+1 방지.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceHlsLookupService {

    private final EvidenceHlsRepository evidenceHlsRepository;

    public Map<Long, EvidenceHls> findByEvidenceIds(Collection<Long> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = evidenceIds.stream().distinct().toList();
        return evidenceHlsRepository.findByEvidenceIdIn(distinctIds).stream()
                .collect(Collectors.toMap(EvidenceHls::getEvidenceId, Function.identity()));
    }

    /**
     * case summary / DTO용. VIDEO가 아니면 null, 행 없으면 PENDING.
     */
    public HlsStatus resolveStatus(FileType fileType, Long evidenceId, Map<Long, EvidenceHls> hlsByEvidenceId) {
        if (fileType != FileType.VIDEO) {
            return null;
        }
        EvidenceHls hls = hlsByEvidenceId.get(evidenceId);
        return hls != null ? hls.getHlsStatus() : HlsStatus.PENDING;
    }
}
