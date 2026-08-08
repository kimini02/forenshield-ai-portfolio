package com.example.demo.service.readiness;

import com.example.demo.config.ReadinessProperties;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceReadinessAcknowledgementTest {

    @Mock
    private EvidenceRepository evidenceRepository;
    @Mock
    private EvidenceMetadataRepository evidenceMetadataRepository;
    @Mock
    private VideoReadinessRunner videoReadinessRunner;
    @Mock
    private EvidenceReadinessFileService evidenceReadinessFileService;
    @Mock
    private ReadinessProperties readinessProperties;

    private EvidenceReadinessService evidenceReadinessService;

    @BeforeEach
    void setUp() {
        evidenceReadinessService = new EvidenceReadinessService(
                evidenceRepository,
                evidenceMetadataRepository,
                new ReadinessEvaluator(),
                videoReadinessRunner,
                evidenceReadinessFileService,
                readinessProperties,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("requiresAcknowledgement=true 이고 acknowledge 미전달 시 409")
    void assertQualityAcknowledged_withoutAck_throwsConflict() {
        EvidenceMetadata metadata = poorMetadata();
        when(evidenceMetadataRepository.findByEvidenceId(1L)).thenReturn(Optional.of(metadata));

        assertThatThrownBy(() -> evidenceReadinessService.assertQualityAcknowledged(1L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getStatus().value()).isEqualTo(409);
                    assertThat(businessException.getErrorCode()).isEqualTo("QUALITY_WARNING_REQUIRED");
                });
    }

    @Test
    @DisplayName("requiresAcknowledgement=true 이고 acknowledge=true 이면 통과")
    void assertQualityAcknowledged_withAck_passes() {
        EvidenceMetadata metadata = poorMetadata();
        when(evidenceMetadataRepository.findByEvidenceId(1L)).thenReturn(Optional.of(metadata));

        assertThatCode(() -> evidenceReadinessService.assertQualityAcknowledged(1L, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("GOOD 등급이면 acknowledge 없이도 통과")
    void assertQualityAcknowledged_goodTier_passesWithoutAck() {
        EvidenceMetadata metadata = new EvidenceMetadata();
        metadata.setEvidenceId(1L);
        metadata.setExtractionStatus(ExtractionStatus.SUCCESS);
        metadata.setWidth(1920);
        metadata.setHeight(1080);
        metadata.setDurationSec(30);
        metadata.setFps(30.0);
        when(evidenceMetadataRepository.findByEvidenceId(1L)).thenReturn(Optional.of(metadata));

        assertThatCode(() -> evidenceReadinessService.assertQualityAcknowledged(1L, null))
                .doesNotThrowAnyException();
    }

    private EvidenceMetadata poorMetadata() {
        EvidenceMetadata metadata = new EvidenceMetadata();
        metadata.setEvidenceId(1L);
        metadata.setExtractionStatus(ExtractionStatus.SUCCESS);
        metadata.setWidth(320);
        metadata.setHeight(240);
        metadata.setDurationSec(30);
        metadata.setFps(30.0);
        metadata.setReadinessJson("""
                {
                  "readinessTier": "POOR",
                  "confidenceCap": 60,
                  "requiresAcknowledgement": true,
                  "reasons": ["해상도 320x240"]
                }
                """);
        return metadata;
    }
}
