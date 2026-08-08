package com.example.demo.controller;

import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.repository.EvidenceHlsRepository;
import com.example.demo.support.AbstractEvidenceIntegrationTest;
import com.example.demo.support.StepUpTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvidenceHlsControllerTest extends AbstractEvidenceIntegrationTest {

    @Autowired
    private EvidenceHlsRepository evidenceHlsRepository;

    @Test
    @DisplayName("detail 응답에 hlsPlayback과 streamToken이 포함된다")
    void detail_includesHlsPlayback() throws Exception {
        long evidenceId = uploadVideoEvidence("hls-playback.mp4");

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hlsPlayback.manifestPath")
                        .value("/api/v1/evidences/" + evidenceId + "/hls/master.m3u8"))
                .andExpect(jsonPath("$.hlsPlayback.hlsStatus").value("PENDING"))
                .andExpect(jsonPath("$.hlsPlayback.streamToken").isString())
                .andExpect(jsonPath("$.hlsPlayback.expiresIn").value(900));
    }

    @Test
    @DisplayName("streamToken 없이 HLS key 요청 시 403")
    void key_withoutStreamToken_returnsForbidden() throws Exception {
        long evidenceId = uploadVideoEvidence("hls-key-guard.mp4");
        markHlsReady(evidenceId);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/hls/key", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STREAM_TOKEN_REQUIRED"));
    }

    @Test
    @DisplayName("step-up 없이 HLS key 요청 시 403")
    void key_withoutStepUp_returnsForbidden() throws Exception {
        long evidenceId = uploadVideoEvidence("hls-stepup-guard.mp4");
        markHlsReady(evidenceId);
        String streamToken = fetchStreamToken(evidenceId);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/hls/key", evidenceId)
                        .param("streamToken", streamToken)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STEP_UP_REQUIRED"));
    }

    @Test
    @DisplayName("streamToken 없이 HLS manifest 요청 시 403")
    void manifest_withoutStreamToken_returnsForbidden() throws Exception {
        long evidenceId = uploadVideoEvidence("hls-manifest-guard.mp4");
        markHlsReady(evidenceId);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/hls/master.m3u8", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STREAM_TOKEN_REQUIRED"));
    }

    private long uploadVideoEvidence(String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "video/mp4",
                "video bytes".getBytes()
        );
        MvcResult upload = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", "HLS 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(upload.getResponse().getContentAsString()).get("evidenceId").asLong();
    }

    private String fetchStreamToken(long evidenceId) throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(detail.getResponse().getContentAsString())
                .path("hlsPlayback")
                .path("streamToken")
                .asText();
    }

    private void markHlsReady(long evidenceId) {
        LocalDateTime now = LocalDateTime.now();
        EvidenceHls row = EvidenceHls.createPending(evidenceId, now);
        row.markReady("hls/" + evidenceId + "/", new byte[] {1, 2, 3}, now);
        evidenceHlsRepository.save(row);
    }
}
