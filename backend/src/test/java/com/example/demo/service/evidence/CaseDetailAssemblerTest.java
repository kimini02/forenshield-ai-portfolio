package com.example.demo.service.evidence;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.service.evidence.hls.EvidenceHlsLookupService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseDetailAssemblerTest {

    @Mock
    private CaseEvidencePresentationService caseEvidencePresentationService;

    @Mock
    private EvidenceHlsLookupService evidenceHlsLookupService;

    private CaseDetailAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new CaseDetailAssembler(
                caseEvidencePresentationService,
                evidenceHlsLookupService
        );
    }

    @Test
    void assemble_processingEvidenceSetsCaseStatusToProcessing() {
        User user = mock(User.class);

        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(10L);
        when(evidence.getUploaderId()).thenReturn(99L);
        when(evidence.getCaseName()).thenReturn("case-a");
        when(evidence.getFileName()).thenReturn("video.mp4");
        when(evidence.getFileType()).thenReturn(FileType.VIDEO);
        when(evidence.getUploadedAt()).thenReturn(LocalDateTime.now());

        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(10L);
        request.setStatus(AnalysisStatus.ANALYZING);
        request.setProgressPercent(40);

        when(caseEvidencePresentationService.orderForDisplay(List.of(evidence))).thenReturn(List.of(evidence));
        when(caseEvidencePresentationService.resolveRepresentativeEvidenceId(eq(user), eq("case-a"), eq(List.of(evidence))))
                .thenReturn(java.util.Optional.of(10L));
        when(caseEvidencePresentationService.resolveDisplayLabel(eq(evidence), any())).thenReturn("증거 1");
        when(caseEvidencePresentationService.lifecycleStatusName(evidence)).thenReturn("ACTIVE");
        when(caseEvidencePresentationService.roleName(evidence)).thenReturn("PRIMARY");
        when(evidenceHlsLookupService.resolveStatus(FileType.VIDEO, 10L, Map.of()))
                .thenReturn(HlsStatus.PENDING);

        var response = assembler.assemble(
                user,
                "case-a",
                List.of(evidence),
                List.of(request),
                null,
                user,
                Map.of()
        );

        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(response.getEvidences()).hasSize(1);
        assertThat(response.getEvidences().get(0).getAnalysisProgress()).isEqualTo(40);
        assertThat(response.getEvidences().get(0).getHlsStatus()).isEqualTo("PENDING");
    }
}
