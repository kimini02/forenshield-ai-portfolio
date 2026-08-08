package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EvidenceDetailResponse {

    private EvidenceInfoDto evidenceInfo;
    private IntegrityInfoDto integrityInfo;
    private ManifestInfoDto manifestInfo;
    private SignatureInfoDto signatureInfo;
    private BlockchainInfoDto blockchainInfo;
    private AnalysisInfoDto analysisInfo;
    private ReportInfoDto reportInfo;
    private List<CocLogDto> cocLogs;
    private HlsPlaybackDto hlsPlayback;
}
