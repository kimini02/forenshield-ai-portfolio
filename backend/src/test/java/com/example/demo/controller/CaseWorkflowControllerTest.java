package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.EvidenceLifecycleStatus;
import com.example.demo.domain.enums.EvidenceRole;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class CaseWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userToken;
    private User testUser;
    private Evidence primaryEvidence;
    private Evidence supplementEvidence;

    @BeforeEach
    void setUp() throws Exception {
        caseProfileRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .loginId("1111")
                .email("1111@local.dev")
                .password(passwordEncoder.encode("2222"))
                .name("테스트 사용자")
                .organizationType(OrgType.ETC)
                .department("로컬개발팀")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        userToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");

        primaryEvidence = saveEvidence("primary.mp4", 101L);
        supplementEvidence = saveEvidence("supplement.mp4", 102L);
    }

    @AfterEach
    void tearDown() {
        caseProfileRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSetRepresentativeEvidence() throws Exception {
        mockMvc.perform(patch("/api/v1/cases/representative?caseKey=v2-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evidenceId": %d}
                                """.formatted(primaryEvidence.getEvidenceId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cases?caseKey=v2-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representativeEvidenceId").value(primaryEvidence.getEvidenceId()))
                .andExpect(jsonPath("$.evidences[0].role").value("PRIMARY"));
    }

    @Test
    void shouldExcludeEvidence() throws Exception {
        mockMvc.perform(patch("/api/v1/evidences/" + supplementEvidence.getEvidenceId() + "/exclude")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"잘못 업로드"}
                                """))
                .andExpect(status().isNoContent());

        Evidence updated = evidenceRepository.findById(supplementEvidence.getEvidenceId()).orElseThrow();
        assertThat(updated.getLifecycleStatus()).isEqualTo(EvidenceLifecycleStatus.EXCLUDED);
        assertThat(updated.getExcludedReason()).isEqualTo("잘못 업로드");

        mockMvc.perform(get("/api/v1/cases?caseKey=v2-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidences[1].lifecycleStatus").value("EXCLUDED"));
    }

    @Test
    void shouldRenameCase() throws Exception {
        mockMvc.perform(patch("/api/v1/cases?caseKey=v2-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseName":"renamed-case"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("renamed-case"))
                .andExpect(jsonPath("$.caseName").value("renamed-case"));

        Evidence updated = evidenceRepository.findById(primaryEvidence.getEvidenceId()).orElseThrow();
        assertThat(updated.getCaseName()).isEqualTo("renamed-case");
        assertThat(updated.getCaseNumber()).isEqualTo("renamed-case");

        mockMvc.perform(get("/api/v1/cases?caseKey=renamed-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidences", hasSize(2)));
    }

    @Test
    void shouldExposeAnalysisProgressInCaseDetail() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(primaryEvidence.getEvidenceId());
        request.setRequestedBy(testUser.getUserId());
        request.setStatus(AnalysisStatus.ANALYZING);
        request.setProgressPercent(42);
        request.setRequestedAt(LocalDateTime.now());
        analysisRequestRepository.save(request);

        mockMvc.perform(get("/api/v1/cases?caseKey=v2-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidences[0].analysisProgress").value(42))
                .andExpect(jsonPath("$.evidences[0].displayLabel").value("증거 1"));
    }

    @Test
    void shouldReplaceEvidence() throws Exception {
        MockMultipartFile replacement = new MockMultipartFile(
                "file",
                "replacement.mp4",
                "video/mp4",
                new byte[] {1, 2, 3, 4, 5, 6, 7, 8}
        );

        mockMvc.perform(multipart("/api/v1/evidences/" + supplementEvidence.getEvidenceId() + "/replace")
                        .file(replacement)
                        .param("reason", "새 파일로 대체")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.evidenceId").exists());

        Evidence replaced = evidenceRepository.findById(supplementEvidence.getEvidenceId()).orElseThrow();
        assertThat(replaced.getLifecycleStatus()).isEqualTo(EvidenceLifecycleStatus.REPLACED);
        assertThat(replaced.getReplacementEvidenceId()).isNotNull();
    }

    private Evidence saveEvidence(String fileName, long suffix) {
        return evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("v2-case")
                .caseNumber("v2-case")
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(1024L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("a".repeat(64))
                .originalStoragePath("s3://bucket/" + suffix)
                .uploadedAt(LocalDateTime.now())
                .build());
    }
}
