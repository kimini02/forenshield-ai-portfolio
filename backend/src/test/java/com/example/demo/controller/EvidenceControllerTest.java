package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.EvidenceManifestRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.NotificationType;
import com.example.demo.domain.enums.SecurityAlertCode;
import com.example.demo.support.AbstractEvidenceIntegrationTest;
import com.example.demo.support.EvidenceApiTestSupport;
import com.example.demo.support.EvidenceTestFixtures;
import com.example.demo.support.StepUpTestSupport;
import com.example.demo.service.evidence.EvidenceStoragePaths;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvidenceControllerTest extends AbstractEvidenceIntegrationTest {

    @Test
    @DisplayName("인증 없이 증거 API 호출 시 401")
    void shouldRejectUnauthorizedUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-file.mp4",
                "video/mp4",
                "Hello, World!".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void shouldUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-file.mp4",
                "video/mp4",
                "Hello, World!".getBytes()
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "테스트 사건")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("파일 업로드 완료"))
                .andExpect(jsonPath("$.fileName").value("test-file.mp4"))
                .andExpect(jsonPath("$.fileSize").value(file.getSize()))
                .andExpect(jsonPath("$.evidenceId").exists())
                .andExpect(jsonPath("$.hashAlgorithm").value("SHA-256"))
                .andExpect(jsonPath("$.hashValue").isString())
                .andExpect(jsonPath("$.originalSha256").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        EvidenceApiTestSupport.assertUploadHashFieldsMatch(objectMapper, responseBody);
    }

    @Test
    void shouldReturnErrorWhenCaseNameIsBlank() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "no-case-name.mp4",
                "video/mp4",
                "No Case Name".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", " ")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("사건명을 입력해 주세요."));
    }

    @Test
    @DisplayName("사건명을 포함하여 파일을 업로드하면 DB에 사건명이 저장되어야 한다")
    void shouldUploadFileWithCaseName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "case-test.mp4",
                "video/mp4",
                "Case Name Test Data".getBytes()
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.caseName").value(caseName));

        assertThat(evidenceRepository.findAll())
                .anyMatch(evidence -> caseName.equals(evidence.getCaseName()));
    }

    @Test
    @DisplayName("업로드하면 S3 파일명에 사건명-증거번호(evidenceId)가 반영된다")
    void shouldUploadFileWithEvidenceIdInStoragePath() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "secret-name.mp4",
                "video/mp4",
                "Evidence Id Test Data".getBytes()
        );
        String caseName = "딥페이크 테스트";

        mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseName").value(caseName));

        assertThat(evidenceRepository.findAll())
                .anyMatch(evidence ->
                        caseName.equals(evidence.getCaseName())
                                && evidence.getOriginalStoragePath().endsWith(
                                        EvidenceStoragePaths.storedObjectFileName(evidence, evidence.getFileName())));
    }

    @Test
    void shouldReturnErrorWhenFileIsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "빈 파일 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("업로드된 파일이 없습니다."));
    }

    @Test
    void shouldUploadImageFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                "fake-image-data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "영상 업로드 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("test-video.mp4"))
                .andExpect(jsonPath("$.hashValue").isString());
    }

    @Test
    void shouldReturnBrokenMetadataForInvalidMediaFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken-video.mp4",
                "video/mp4",
                "not-a-video".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "깨진 메타데이터 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.metadata.extractionStatus").value("FAILED"))
                .andExpect(jsonPath("$.hashValue").isString());
    }

    @Test
    @DisplayName("동일 파일 2회 업로드 시 동일한 SHA-256 해시값을 반환한다")
    void upload_sameFileTwice_returnsSameHash() throws Exception {
        byte[] content = "sample mp4 content for integrity test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile sampleFile = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                content
        );

        String firstResponse = mockMvc.perform(multipart("/api/v1/evidences/upload").file(sampleFile)
                        .param("caseName", "동일 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashAlgorithm").value("SHA-256"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstHash = EvidenceApiTestSupport.extractHashValue(firstResponse);
        assertThat(EvidenceApiTestSupport.extractOriginalSha256(firstResponse)).isEqualTo(firstHash);

        MockMultipartFile sameFileAgain = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                content
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(sameFileAgain)
                        .param("caseName", "동일 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue").value(firstHash))
                .andExpect(jsonPath("$.originalSha256").value(firstHash));
    }

    @Test
    @DisplayName("수정된 파일 업로드 시 다른 SHA-256 해시값을 반환한다")
    void upload_modifiedFile_returnsDifferentHash() throws Exception {
        byte[] originalContent = "original sample mp4 content".getBytes(StandardCharsets.UTF_8);
        byte[] modifiedContent = "original sample mp4 content-modified".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile originalFile = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                originalContent
        );

        String originalResponse = mockMvc.perform(multipart("/api/v1/evidences/upload").file(originalFile)
                        .param("caseName", "수정 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        MockMultipartFile modifiedFile = new MockMultipartFile(
                "file",
                "sample_modified.mp4",
                "video/mp4",
                modifiedContent
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(modifiedFile)
                        .param("caseName", "수정 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue", not(EvidenceApiTestSupport.extractHashValue(originalResponse))))
                .andExpect(jsonPath("$.originalSha256", not(EvidenceApiTestSupport.extractOriginalSha256(originalResponse))));
    }

    @Test
    @DisplayName("영상 분석 건수 통계를 조회할 수 있다")
    void shouldReturnMediaStats() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "sample-a.mp4",
                "video/mp4",
                "image bytes".getBytes()
        );
        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "sample-b.mp4",
                "video/mp4",
                "video bytes".getBytes()
        );
        MockMultipartFile audioFile = new MockMultipartFile(
                "file",
                "sample-c.mp4",
                "video/mp4",
                "audio bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(imageFile)
                        .param("caseName", "미디어별 건수 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/evidences/upload").file(videoFile)
                        .param("caseName", "미디어별 건수 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/evidences/upload").file(audioFile)
                        .param("caseName", "미디어별 건수 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/evidences/stats").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnalysisCount").value(0))
                .andExpect(jsonPath("$.deepfakeDetectedCount").value(0))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.inProgressCount").value(0));

        long imageEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("sample-a.mp4"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();
        long videoEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("sample-b.mp4"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();
        long audioEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("sample-c.mp4"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "2026-서울-0123 딥페이크 유포 사건",
                                  "evidenceIds": [%d, %d, %d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(imageEvidenceId, videoEvidenceId, audioEvidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(3));

        mockMvc.perform(get("/api/v1/evidences/stats").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnalysisCount").value(3))
                .andExpect(jsonPath("$.deepfakeDetectedCount").value(0))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.inProgressCount").value(3));
    }

    @Test
    @DisplayName("RQ-DSH-044: 최근 7일 분석 완료 추이를 조회할 수 있다")
    void shouldReturnAnalysisTrend() throws Exception {
        User user = currentUser();
        LocalDateTime now = LocalDateTime.now();

        Evidence evidenceToday = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("trend-today.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("d".repeat(64))
                .originalStoragePath("original/trend-today.mp4")
                .uploadedAt(now)
                .build());
        analysisRequestRepository.save(EvidenceTestFixtures.completedRequest(
                evidenceToday.getEvidenceId(), user.getUserId(), now));

        Evidence evidenceYesterday = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("trend-yesterday.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("e".repeat(64))
                .originalStoragePath("original/trend-yesterday.mp4")
                .uploadedAt(now.minusDays(1))
                .build());
        analysisRequestRepository.save(EvidenceTestFixtures.completedRequest(
                evidenceYesterday.getEvidenceId(), user.getUserId(), now.minusDays(1)));
        analysisRequestRepository.save(EvidenceTestFixtures.completedRequest(
                evidenceYesterday.getEvidenceId(), user.getUserId(), now.minusDays(1).minusHours(2)));

        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(get("/api/v1/evidences/stats/trend")
                        .param("days", "7")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.points[6].date").value(today))
                .andExpect(jsonPath("$.points[6].completedCount").value(1))
                .andExpect(jsonPath("$.points[5].date").value(yesterday))
                .andExpect(jsonPath("$.points[5].completedCount").value(2));
    }

    @Test
    @DisplayName("분석 추이 days 파라미터가 범위를 벗어나면 400을 반환한다")
    void analysisTrend_invalidDays_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/stats/trend")
                        .param("days", "31")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("RQ-DSH-045: 최근 분석 이력 위젯을 조회할 수 있다")
    void shouldReturnRecentAnalyses() throws Exception {
        User user = currentUser();
        LocalDateTime now = LocalDateTime.now();

        Evidence newest = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("recent-newest.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("f".repeat(64))
                .originalStoragePath("original/recent-newest.mp4")
                .uploadedAt(now)
                .build());
        AnalysisRequest newestRequest = analysisRequestRepository.save(
                EvidenceTestFixtures.completedRequest(newest.getEvidenceId(), user.getUserId(), now));
        newestRequest.setRequestedAt(now.minusMinutes(1));
        analysisRequestRepository.save(newestRequest);
        saveAnalysisResult(newestRequest, 72.5, RiskLevel.HIGH);

        Evidence older = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("recent-older.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("g".repeat(64))
                .originalStoragePath("original/recent-older.mp4")
                .uploadedAt(now.minusHours(2))
                .build());
        AnalysisRequest olderRequest = analysisRequestRepository.save(
                EvidenceTestFixtures.completedRequest(older.getEvidenceId(), user.getUserId(), now.minusHours(1)));
        saveAnalysisResult(olderRequest, 15.0, RiskLevel.LOW);

        Evidence processing = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("recent-processing.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("h".repeat(64))
                .originalStoragePath("original/recent-processing.mp4")
                .uploadedAt(now.minusMinutes(30))
                .build());
        AnalysisRequest processingRequest = new AnalysisRequest();
        processingRequest.setEvidenceId(processing.getEvidenceId());
        processingRequest.setRequestedBy(user.getUserId());
        processingRequest.setStatus(AnalysisStatus.ANALYZING);
        processingRequest.setRequestedAt(now.minusMinutes(10));
        processingRequest.setStartedAt(now.minusMinutes(5));
        processingRequest.setProgressPercent(40);
        analysisRequestRepository.save(processingRequest);

        mockMvc.perform(get("/api/v1/evidences/stats/recent")
                        .param("limit", "5")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].fileName").value("recent-newest.mp4"))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].riskScore").value(72.5))
                .andExpect(jsonPath("$.items[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.items[0].verdictIndicator").value("DANGER"))
                .andExpect(jsonPath("$.items[1].fileName").value("recent-processing.mp4"))
                .andExpect(jsonPath("$.items[1].status").value("PROCESSING"))
                .andExpect(jsonPath("$.items[1].riskScore").doesNotExist())
                .andExpect(jsonPath("$.items[2].fileName").value("recent-older.mp4"))
                .andExpect(jsonPath("$.items[2].verdictIndicator").value("NORMAL"));
    }

    @Test
    @DisplayName("최근 분석 위젯은 증거당 최신 요청 1건만 반환한다")
    void recentAnalyses_deduplicatesByEvidence() throws Exception {
        User user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        Evidence evidence = saveVideoEvidence(user, "recent-dedupe.mp4");

        AnalysisRequest oldRequest = new AnalysisRequest();
        oldRequest.setEvidenceId(evidence.getEvidenceId());
        oldRequest.setRequestedBy(user.getUserId());
        oldRequest.setStatus(AnalysisStatus.COMPLETED);
        oldRequest.setRequestedAt(now.minusDays(1));
        oldRequest.setCompletedAt(now.minusDays(1));
        oldRequest.setProgressPercent(100);
        analysisRequestRepository.save(oldRequest);

        AnalysisRequest latestRequest = analysisRequestRepository.save(
                EvidenceTestFixtures.completedRequest(evidence.getEvidenceId(), user.getUserId(), now));

        mockMvc.perform(get("/api/v1/evidences/stats/recent")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].analysisRequestId").value(latestRequest.getAnalysisRequestId()));
    }

    @Test
    @DisplayName("최근 분석 limit 파라미터가 범위를 벗어나면 400을 반환한다")
    void recentAnalyses_invalidLimit_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/stats/recent")
                        .param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("RQ-DSH-041: 서비스 소개 및 바로가기 카드 정보를 조회할 수 있다")
    void shouldReturnDashboardIntro() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/dashboard/intro")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badgeLabel").value("디지털 포렌식 증거 검증 플랫폼"))
                .andExpect(jsonPath("$.titleLine1").value("디지털 미디어 파일"))
                .andExpect(jsonPath("$.titleLine2").value("분석 대시보드"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.shortcuts.length()").value(2))
                .andExpect(jsonPath("$.shortcuts[0].label").value("분석 시작하기"))
                .andExpect(jsonPath("$.shortcuts[0].actionType").value("IN_APP"))
                .andExpect(jsonPath("$.shortcuts[0].actionTarget").value("#new-analysis"))
                .andExpect(jsonPath("$.shortcuts[1].label").value("비교 검증"))
                .andExpect(jsonPath("$.shortcuts[1].actionType").value("ROUTE"))
                .andExpect(jsonPath("$.shortcuts[1].actionTarget").value("/compare"))
                .andExpect(jsonPath("$.trustHighlights.length()").value(3))
                .andExpect(jsonPath("$.trustHighlights[0].label").value("CoC 감사 추적"))
                .andExpect(jsonPath("$.trustHighlights[0].iconKey").value("history"));
    }

    @Test
    @DisplayName("서비스 소개 조회는 인증이 필요하다")
    void dashboardIntro_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/evidences/dashboard/intro"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("업로드 시 사건명이 없고 분석 요청에도 사건명이 없으면 400을 반환한다")
    void startAnalysis_withoutCaseName_returnsBadRequest() throws Exception {
        User user = currentUser();
        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("analyze-test.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("c".repeat(64))
                .originalStoragePath("original/analyze-test.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(evidence.getEvidenceId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("분석 시작 전 업로드된 증거를 취소하면 소프트 삭제된다")
    void cancelUpload_beforeAnalysis_softDeletesEvidence() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cancel-me.mp4",
                "video/mp4",
                "cancel test".getBytes()
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "업로드 취소 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = Long.parseLong(responseBody.replaceAll(".*\"evidenceId\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNoContent());

        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.DELETED);
        assertThat(evidence.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("분석 대기 중에는 분석 중단 API로 삭제할 수 있다")
    void cancelAnalysis_whenQueued_succeeds() throws Exception {
        User user = currentUser();
        byte[] queuedBytes = "queued analysis bytes".getBytes(StandardCharsets.UTF_8);
        String originalKey = "original/queued.mp4";
        String originalHash = new com.example.demo.service.evidence.HashService().generateSha256(queuedBytes);
        seedS3Object(originalKey, queuedBytes);
        Evidence queuedEvidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("queued.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize((long) queuedBytes.length)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(originalHash)
                .originalStoragePath(originalKey)
                .uploadedAt(LocalDateTime.now())
                .build());
        long evidenceId = queuedEvidence.getEvidenceId();

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "큐 대기 테스트",
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(evidenceId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}/analysis", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNoContent());

        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        assertThat(evidence.getStatus()).isNotEqualTo(EvidenceStatus.DELETED);
        assertThat(evidence.getDeletedAt()).isNull();
        assertThat(analysisRequestRepository.existsByEvidenceId(evidenceId)).isFalse();
    }

    @Test
    @DisplayName("완료된 분석은 중단할 수 없다")
    void cancelAnalysis_whenCompleted_returnsBadRequest() throws Exception {
        User user = currentUser();
        Evidence evidence = saveVideoEvidence(user, "completed.mp4");
        AnalysisRequest request = saveAnalysisRequest(
                evidence,
                user,
                AnalysisStatus.COMPLETED
        );

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}/analysis", evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ANALYSIS_NOT_CANCELABLE"));

        assertThat(analysisRequestRepository.findById(request.getAnalysisRequestId()))
                .isPresent()
                .get()
                .extracting(AnalysisRequest::getStatus)
                .isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("실패한 분석은 중단할 수 없다")
    void cancelAnalysis_whenFailed_returnsBadRequest() throws Exception {
        User user = currentUser();
        Evidence evidence = saveVideoEvidence(user, "failed.mp4");
        AnalysisRequest request = saveAnalysisRequest(
                evidence,
                user,
                AnalysisStatus.FAILED
        );

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}/analysis", evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ANALYSIS_NOT_CANCELABLE"));

        assertThat(analysisRequestRepository.findById(request.getAnalysisRequestId()))
                .isPresent()
                .get()
                .extracting(AnalysisRequest::getStatus)
                .isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("분석 실패 시 analysis-status가 errorCode·errorMessage를 반환한다")
    void getAnalysisStatus_whenFailed_returnsErrorDetails() throws Exception {
        User user = currentUser();
        Evidence evidence = saveVideoEvidence(user, "status-failed.mp4");
        AnalysisRequest request = saveAnalysisRequest(
                evidence,
                user,
                AnalysisStatus.FAILED
        );
        request.setErrorCode("ANALYSIS_FAILED");
        request.setErrorMessage("모델 추론 중 오류가 발생했습니다.");
        analysisRequestRepository.save(request);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/analysis-status", evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("ANALYSIS_FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("모델 추론 중 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("분석 완료 시 analysis-status는 errorCode·errorMessage를 포함하지 않는다")
    void getAnalysisStatus_whenCompleted_omitsErrorDetails() throws Exception {
        User user = currentUser();
        Evidence evidence = saveVideoEvidence(user, "status-completed.mp4");
        saveAnalysisRequest(evidence, user, AnalysisStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/analysis-status", evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    @DisplayName("soft COMPLETED(NO_HUMAN_FACE) 시 analysis-status가 advisory errorCode를 반환한다")
    void getAnalysisStatus_whenSoftCompleted_returnsAdvisory() throws Exception {
        User user = currentUser();
        Evidence evidence = saveVideoEvidence(user, "status-soft-completed.mp4");
        AnalysisRequest request = saveAnalysisRequest(
                evidence,
                user,
                AnalysisStatus.COMPLETED
        );
        request.setErrorCode("NO_HUMAN_FACE");
        request.setErrorMessage("사람 얼굴이 검출되지 않아 딥페이크 판별을 수행할 수 없습니다.");
        analysisRequestRepository.save(request);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/analysis-status", evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.errorCode").value("NO_HUMAN_FACE"))
                .andExpect(jsonPath("$.errorMessage").value("사람 얼굴이 검출되지 않아 딥페이크 판별을 수행할 수 없습니다."));
    }

    @Test
    @DisplayName("분석 시작 후에는 업로드 취소가 불가하다")
    void cancelUpload_afterAnalysisStarted_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "locked.mp4",
                "video/mp4",
                "locked test".getBytes()
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "취소 불가 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = Long.parseLong(responseBody.replaceAll(".*\"evidenceId\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "취소 불가 테스트",
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(evidenceId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ANALYSIS_ALREADY_STARTED"));
    }

    @Test
    @DisplayName("업로드 응답의 해시값이 DB에 저장된다")
    void upload_success_persistsEvidenceWithHash() throws Exception {
        long beforeCount = evidenceRepository.count();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                "sample wav bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "해시 저장 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue").value(org.hamcrest.Matchers.matchesRegex("[0-9a-f]{64}")));

        assertThat(evidenceRepository.count()).isEqualTo(beforeCount + 1);
        assertThat(evidenceRepository.findAll())
                .allMatch(evidence -> evidence.getHashAlgorithm().equals("SHA-256"))
                .allMatch(evidence -> evidence.getHashValue().length() == 64)
                .allMatch(evidence -> evidence.getOriginalStoragePath().endsWith(
                        "/original/" + EvidenceStoragePaths.storedObjectFileName(evidence, evidence.getFileName())))
                .allMatch(evidence -> !evidence.getOriginalStoragePath().contains("sample.mp4"));
    }

    @Test
    @DisplayName("파일 업로드 성공 시 CoC 로그 3개를 ERD 이벤트명과 해시 체인으로 저장한다")
    void upload_success_recordsCustodyLogs() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "coc-test.mp4",
                "video/mp4",
                "coc test image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";
        String previousLogHash = custodyLogRepository.findTopByOrderByLogIdDesc()
                .map(CustodyLog::getCurrentLogHash)
                .orElse(null);

        String responseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.hashValue").value(org.hamcrest.Matchers.matchesRegex("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.originalSha256").value(org.hamcrest.Matchers.matchesRegex("[0-9a-f]{64}")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        long evidenceId = response.get("evidenceId").asLong();
        String hashValue = response.get("hashValue").asText();
        assertThat(response.get("originalSha256").asText()).isEqualTo(hashValue);

        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        assertThat(logs).hasSize(3);
        assertThat(logs)
                .extracting(CustodyLog::getActionType)
                .containsExactly("EVIDENCE_UPLOADED", "HASH_CREATED", "METADATA_EXTRACTED");
        assertThat(logs)
                .allSatisfy(log -> {
                    assertThat(log.getTargetType()).isEqualTo(CustodyTargetType.EVIDENCE);
                    assertThat(log.getTargetId()).isEqualTo(evidenceId);
                    assertThat(log.getSubjectHash()).isEqualTo(hashValue);
                    assertThat(log.getStoragePathAtEvent()).isNotBlank();
                    assertThat(log.getCurrentLogHash()).matches("[0-9a-f]{64}");
                });

        assertThat(logs.get(0).getPreviousLogHash()).isEqualTo(previousLogHash);
        assertThat(logs.get(1).getPreviousLogHash()).isEqualTo(logs.get(0).getCurrentLogHash());
        assertThat(logs.get(2).getPreviousLogHash()).isEqualTo(logs.get(1).getCurrentLogHash());

        JsonNode uploadPayload = objectMapper.readTree(logs.get(0).getEventPayloadJson());
        assertThat(uploadPayload.get("fileName").asText()).isEqualTo("coc-test.mp4");
        assertThat(uploadPayload.get("fileType").asText()).isEqualTo("VIDEO");
        assertThat(uploadPayload.get("mimeType").asText()).isEqualTo("video/mp4");
        assertThat(uploadPayload.get("fileSize").asLong()).isEqualTo(file.getSize());
        assertThat(uploadPayload.get("caseName").asText()).isEqualTo(caseName);

        JsonNode hashPayload = objectMapper.readTree(logs.get(1).getEventPayloadJson());
        assertThat(hashPayload.get("hashAlgorithm").asText()).isEqualTo("SHA-256");
        assertThat(hashPayload.get("hashValue").asText()).isEqualTo(hashValue);

        JsonNode metadataPayload = objectMapper.readTree(logs.get(2).getEventPayloadJson());
        assertThat(metadataPayload.get("extractionStatus").asText()).isIn("SUCCESS", "FAILED");

        assertThat(custodyLogRepository.findAll())
                .extracting(CustodyLog::getActionType)
                .doesNotContain("FILE_UPLOADED", "ORIGINAL_HASH_CREATED");
    }

    @Test
    @DisplayName("증거 상세 API는 Step-up 토큰 없이 403 STEP_UP_REQUIRED를 반환한다")
    void getEvidenceDetail_withoutStepUpToken_returnsForbidden() throws Exception {
        long evidenceId = uploadAndStartAnalysis("step-up-guard.mp4", "Step-up 가드 사건");

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STEP_UP_REQUIRED"));
    }

    @Test
    @DisplayName("증거 상세 API는 유효한 Step-up 토큰으로 200을 반환한다")
    void getEvidenceDetail_withValidStepUpToken_returnsOk() throws Exception {
        long evidenceId = uploadAndStartAnalysis("step-up-ok.mp4", "Step-up 성공 사건");

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceInfo.evidenceId").value(evidenceId));
    }

    @Test
    @DisplayName("증거 상세 API는 잘못된 Step-up 토큰으로 403 STEP_UP_REQUIRED를 반환한다")
    void getEvidenceDetail_withInvalidStepUpToken_returnsForbidden() throws Exception {
        long evidenceId = uploadAndStartAnalysis("step-up-invalid.mp4", "Step-up 실패 사건");

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, "invalid-step-up-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STEP_UP_REQUIRED"));
    }

    @Test
    @DisplayName("증거 상세 API는 프론트 상세 페이지가 사용하는 필드를 포함한다")
    void getEvidenceDetail_returnsFrontendCompatibleFields() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "detail-page.mp4",
                "video/mp4",
                "detail page image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "상세 페이지 연동 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceInfo.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.evidenceInfo.fileName").value("detail-page.mp4"))
                .andExpect(jsonPath("$.evidenceInfo.caseName").value(caseName))
                .andExpect(jsonPath("$.evidenceInfo.mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.evidenceInfo.fileType").value("VIDEO"))
                .andExpect(jsonPath("$.evidenceInfo.technicalMetadata.extractionStatus").isString())
                .andExpect(jsonPath("$.integrityInfo.chainValid").isBoolean())
                .andExpect(jsonPath("$.integrityInfo.isChainValid").isBoolean())
                .andExpect(jsonPath("$.integrityInfo.recoveryScore").isNumber())
                .andExpect(jsonPath("$.integrityInfo.dataLossPercent").isNumber())
                .andExpect(jsonPath("$.integrityInfo.recoveryGrade").isString())
                .andExpect(jsonPath("$.analysisInfo.status").value("PENDING"))
                .andExpect(jsonPath("$.analysisInfo.moduleResults").isArray())
                .andExpect(jsonPath("$.analysisInfo.moduleResults").isEmpty())
                .andExpect(jsonPath("$.signatureInfo.signatureStatus").value("SIGNED"))
                .andExpect(jsonPath("$.signatureInfo.signatureValid").value(true))
                .andExpect(jsonPath("$.manifestInfo.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.cocLogs").isArray())
                .andExpect(jsonPath("$.cocLogs").isNotEmpty());

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/coc/verify", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.logCount").isNumber());
    }

    @Test
    @DisplayName("RQ-DTL-075/076: 분석 시작 후 상세 API에 Manifest·전자서명 정보가 포함된다")
    void getEvidenceDetail_afterAnalysisStart_includesManifestAndSignature() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manifest-detail.mp4",
                "video/mp4",
                "manifest detail bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "Manifest 상세 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestInfo.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.manifestInfo.fileId").value(evidenceId))
                .andExpect(jsonPath("$.manifestInfo.caseId").value(caseName))
                .andExpect(jsonPath("$.manifestInfo.caseName").value(caseName))
                .andExpect(jsonPath("$.manifestInfo.uploadedAt").isString())
                .andExpect(jsonPath("$.manifestInfo.originalHash").isString())
                .andExpect(jsonPath("$.manifestInfo.manifestHash").isString())
                .andExpect(jsonPath("$.signatureInfo.signatureStatus").value("SIGNED"))
                .andExpect(jsonPath("$.signatureInfo.signatureValid").value(true));
    }

    @Test
    @DisplayName("분석 요청 성공 시 ANALYSIS_REQUESTED CoC 로그를 저장하고 중복 요청에는 추가하지 않는다")
    void startAnalysis_success_recordsAnalysisRequestedCustodyLog() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analysis-coc.mp4",
                "video/mp4",
                "analysis coc image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        List<CustodyLog> uploadLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(1))
                .andExpect(jsonPath("$.results[0].queueRegistered").value(true))
                .andExpect(jsonPath("$.results[0].queueStatus").value("WAITING"));

        AnalysisRequest analysisRequest = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow();
        List<CustodyLog> analysisLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_REQUEST,
                        analysisRequest.getAnalysisRequestId()
                );

        assertThat(analysisLogs).hasSize(1);
        CustodyLog log = analysisLogs.get(0);
        assertThat(log.getActionType()).isEqualTo("ANALYSIS_REQUESTED");
        assertThat(log.getTargetType()).isEqualTo(CustodyTargetType.ANALYSIS_REQUEST);
        assertThat(log.getTargetId()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(log.getActorId()).isEqualTo(userRepository.findByLoginIdAndDeletedAtIsNull("1111")
                .orElseThrow()
                .getUserId());
        assertThat(log.getSubjectHash()).isEqualTo(evidence.getOriginalHashValue());
        Evidence evidenceAfterAnalyze = evidenceRepository.findById(evidenceId).orElseThrow();
        assertThat(log.getStoragePathAtEvent()).isEqualTo(evidenceAfterAnalyze.getCopyStoragePath());
        assertThat(log.getCurrentLogHash()).matches("[0-9a-f]{64}");
        CustodyLog lastEvidenceLog = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId)
                .stream()
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(log.getPreviousLogHash()).isEqualTo(lastEvidenceLog.getCurrentLogHash());

        List<CustodyLog> evidenceCopyLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);
        assertThat(evidenceCopyLogs)
                .extracting(CustodyLog::getActionType)
                .contains("ANALYSIS_COPY_CREATED", "ANALYSIS_COPY_VERIFIED");

        JsonNode payload = objectMapper.readTree(log.getEventPayloadJson());
        assertThat(payload.get("evidenceId").asLong()).isEqualTo(evidenceId);
        assertThat(payload.get("analysisRequestId").asLong()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(payload.get("status").asText()).isEqualTo("QUEUED");
        assertThat(payload.get("caseName").asText()).isEqualTo(caseName);
        assertThat(payload.get("queueRegistered").asBoolean()).isTrue();
        assertThat(payload.get("queueName").asText()).isEqualTo("forenshield.analysis.queue");
        assertThat(payload.get("fileType").asText()).isEqualTo("video");
        assertThat(payload.get("filePath").asText()).isEqualTo(evidenceAfterAnalyze.getCopyStoragePath());

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(0));

        assertThat(analysisRequestRepository.count()).isEqualTo(1);
        assertThat(custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_REQUEST,
                        analysisRequest.getAnalysisRequestId()
                )).hasSize(1);
        assertThat(custodyLogRepository.findAll())
                .extracting(CustodyLog::getActionType)
                .doesNotContain("QUEUE_REGISTERED", "ANALYSIS_STARTED", "ANALYSIS_COMPLETED",
                        "ANALYSIS_FAILED");
    }

    @Test
    @DisplayName("분석 요청 큐 등록 실패 시 FAILED 상태와 ERROR_OCCURRED CoC 로그를 저장한다")
    void startAnalysis_queuePublishFailure_recordsErrorOccurredCustodyLog() throws Exception {
        doThrow(new RuntimeException("rabbit password=secret token=abc"))
                .when(analysisJobEnqueuer)
                .enqueue(any(AnalysisJobMessage.class));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analysis-queue-fail.mp4",
                "video/mp4",
                "analysis queue fail bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0456 큐 등록 실패 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(0));

        AnalysisRequest analysisRequest = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow();
        assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysisRequest.getErrorCode()).isEqualTo("RABBITMQ_PUBLISH_FAILED");

        List<CustodyLog> analysisLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_REQUEST,
                        analysisRequest.getAnalysisRequestId()
                );

        assertThat(analysisLogs).hasSize(1);
        CustodyLog log = analysisLogs.get(0);
        assertThat(log.getActionType()).isEqualTo("ERROR_OCCURRED");
        assertThat(log.getTargetType()).isEqualTo(CustodyTargetType.ANALYSIS_REQUEST);
        assertThat(log.getTargetId()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(log.getSubjectHash()).isEqualTo(evidence.getOriginalHashValue());
        assertThat(log.getStoragePathAtEvent()).isEqualTo(evidence.getOriginalStoragePath());
        assertThat(log.getCurrentLogHash()).matches("[0-9a-f]{64}");

        JsonNode payload = objectMapper.readTree(log.getEventPayloadJson());
        assertThat(payload.get("step").asText()).isEqualTo("RABBITMQ_PUBLISH");
        assertThat(payload.get("errorCode").asText()).isEqualTo("RABBITMQ_PUBLISH_FAILED");
        assertThat(payload.get("message").asText()).isEqualTo("분석 요청 큐 등록에 실패했습니다.");
        assertThat(payload.get("evidenceId").asLong()).isEqualTo(evidenceId);
        assertThat(payload.get("analysisRequestId").asLong()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(payload.get("queueName").asText()).isEqualTo("forenshield.analysis.queue");
        assertThat(payload.toString().toLowerCase())
                .doesNotContain("password", "token", "secret");

        assertThat(analysisLogs)
                .extracting(CustodyLog::getActionType)
                .doesNotContain("ANALYSIS_REQUESTED");
        assertThat(custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId))
                .extracting(CustodyLog::getActionType)
                .contains("ANALYSIS_COPY_CREATED", "ANALYSIS_COPY_VERIFIED");
        assertThat(custodyLogRepository.findAll())
                .extracting(CustodyLog::getActionType)
                .doesNotContain("QUEUE_REGISTERED", "ANALYSIS_STARTED", "ANALYSIS_COMPLETED");
    }

    @Test
    @DisplayName("RQ-SEC-153/SK-632: 무결성 검증 API는 정상 증거에서 200과 valid=true를 반환한다")
    void verifyIntegrity_validEvidence_returnsOk() throws Exception {
        long evidenceId = uploadAndStartAnalysis("integrity-ok.mp4", "무결성 OK 사건");

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/integrity/verify", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.evidenceId").value(evidenceId));
    }

    @Test
    @DisplayName("RQ-SEC-153/SK-632: 서명 검증 실패 시 integrity/verify는 409와 errorCode를 반환한다")
    void verifyIntegrity_invalidSignature_returnsConflict() throws Exception {
        long evidenceId = uploadAndStartAnalysis("integrity-bad-sig.mp4", "서명 실패 사건");

        EvidenceManifest manifest = evidenceManifestRepository.findById(evidenceId).orElseThrow();
        manifest.setSignatureValue("tampered-signature");
        evidenceManifestRepository.save(manifest);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/integrity/verify", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(SecurityAlertCode.SIGNATURE_INVALID.name()));
    }

    @Test
    @DisplayName("RQ-SEC-153: 상세 조회 시 무결성 실패해도 200이며 SECURITY_ALERT 알림이 생성된다")
    void getEvidenceDetail_onIntegrityFailure_createsSecurityAlert() throws Exception {
        long evidenceId = uploadAndStartAnalysis("detail-alert.mp4", "상세 알림 사건");

        EvidenceManifest manifest = evidenceManifestRepository.findById(evidenceId).orElseThrow();
        manifest.setSignatureValue("tampered-signature");
        evidenceManifestRepository.save(manifest);

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findAll())
                .anyMatch(n -> n.getType() == NotificationType.SECURITY_ALERT
                        && n.getReferenceId().equals(evidenceId)
                        && n.getReferenceType().equals("SEC:SIG_INVALID"));
    }

    @Test
    @DisplayName("화질 POOR 증거는 acknowledge 없이 분석 시작 시 409 QUALITY_WARNING_REQUIRED")
    void startAnalysis_poorQualityWithoutAck_returnsConflict() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "poor-quality.mp4",
                "video/mp4",
                "poor quality video bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-화질-부적합 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElseThrow();
        metadata.setWidth(320);
        metadata.setHeight(240);
        metadata.setDurationSec(30);
        metadata.setFps(30.0);
        evidenceMetadataRepository.save(metadata);
        evidenceReadinessService.seedFfprobeReadiness(evidenceId);

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d]
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("QUALITY_WARNING_REQUIRED"));
    }

    @Test
    @DisplayName("화질 POOR 증거는 acknowledge=true 시 분석 시작 및 QUALITY_WARNING_ACKNOWLEDGED CoC 기록")
    void startAnalysis_poorQualityWithAck_recordsAcknowledgementAndStarts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "poor-quality-ack.mp4",
                "video/mp4",
                "poor quality ack video bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-화질-확인 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElseThrow();
        metadata.setWidth(320);
        metadata.setHeight(240);
        metadata.setDurationSec(30);
        metadata.setFps(30.0);
        evidenceMetadataRepository.save(metadata);
        evidenceReadinessService.seedFfprobeReadiness(evidenceId);

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d],
                                  "acknowledgeQualityWarning": true
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(1));

        List<CustodyLog> evidenceLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);
        assertThat(evidenceLogs)
                .extracting(CustodyLog::getActionType)
                .contains("QUALITY_WARNING_ACKNOWLEDGED");
    }
}
