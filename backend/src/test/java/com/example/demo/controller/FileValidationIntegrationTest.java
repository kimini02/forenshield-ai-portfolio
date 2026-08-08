package com.example.demo.controller;

import com.example.demo.domain.User;
import com.example.demo.exception.FileSizeExceededException;
import com.example.demo.exception.UnsupportedFileTypeException;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.SignupRateLimitService;
import com.example.demo.service.analysis.AnalysisCancelService;
import com.example.demo.service.analysis.AnalysisJobEnqueuer;
import com.example.demo.service.analysis.AnalysisService;
import com.example.demo.service.analysis.AnalysisStatusService;
import com.example.demo.service.auth.StepUpAuthService;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.CocChainVerificationService;
import com.example.demo.service.dashboard.DashboardIntroService;
import com.example.demo.service.evidence.CaseWorkflowService;
import com.example.demo.service.evidence.EvidenceCancelService;
import com.example.demo.service.evidence.EvidenceDetailService;
import com.example.demo.service.dashboard.EvidenceStatsService;
import com.example.demo.service.evidence.FileService;
import com.example.demo.service.integrity.IntegrityVerificationService;
import com.example.demo.service.readiness.EvidenceReadinessService;
import com.example.demo.service.report.ReportPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @MockBean
    private EvidenceStatsService evidenceStatsService;

    @MockBean
    private DashboardIntroService dashboardIntroService;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private AnalysisJobEnqueuer analysisJobEnqueuer;

    @MockBean
    private EvidenceDetailService evidenceDetailService;

    @MockBean
    private EvidenceCancelService evidenceCancelService;

    @MockBean
    private AnalysisCancelService analysisCancelService;

    @MockBean
    private AnalysisStatusService analysisStatusService;

    @MockBean
    private StepUpAuthService stepUpAuthService;

    @MockBean
    private ReportPdfService reportPdfService;

    @MockBean
    private BlockchainAnchorService blockchainAnchorService;

    @MockBean
    private IntegrityVerificationService integrityVerificationService;

    @MockBean
    private EvidenceReadinessService evidenceReadinessService;

    @MockBean
    private CocChainVerificationService cocChainVerificationService;

    @MockBean
    private CaseWorkflowService caseWorkflowService;

    @MockBean
    private AuthUserResolver authUserResolver;

    @MockBean
    private SignupRateLimitService signupRateLimitService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(authUserResolver.requireCurrentUser()).thenReturn(user);
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식 업로드 시 UNSUPPORTED_FILE_TYPE 오류 반환")
    void upload_UnsupportedFileType_ReturnsError() throws Exception {
        when(fileService.upload(any(), any(), any(), any())).thenThrow(new UnsupportedFileTypeException("지원하지 않는 파일 형식입니다. 영상(MP4, MOV) 파일만 업로드할 수 있습니다."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "unsupported content".getBytes());

        mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 파일 형식입니다. 영상(MP4, MOV) 파일만 업로드할 수 있습니다."));
    }

    @Test
    @DisplayName("파일 용량 초과 시 FILE_SIZE_EXCEEDED 오류 반환")
    void upload_FileSizeExceeded_ReturnsError() throws Exception {
        when(fileService.upload(any(), any(), any(), any())).thenThrow(new FileSizeExceededException("VIDEO 파일의 최대 허용 용량은 500MB입니다."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.mp4", "video/mp4", new byte[1024]);

        mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FILE_SIZE_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("VIDEO 파일의 최대 허용 용량은 500MB입니다."));
    }
}
