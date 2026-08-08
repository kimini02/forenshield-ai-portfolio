package com.example.demo.controller;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.InviteCodeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class AdminEvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String userToken;
    private Long evidenceId;

    @BeforeEach
    void setUp() throws Exception {
        custodyLogRepository.deleteAll();
        evidenceRepository.deleteAll();
        inviteCodeRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .loginId("3333")
                .email("3333@local.dev")
                .password(passwordEncoder.encode("4444"))
                .name("관리자")
                .organizationType(OrgType.ETC)
                .department("관리팀")
                .role(UserRole.ROLE_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        User demoUser = userRepository.save(User.builder()
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

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(demoUser.getUserId())
                .caseNumber("CASE-2026-001")
                .caseName("테스트 사건")
                .fileName("sample_video.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(2048L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("b".repeat(64))
                .originalStoragePath("uploads/test/sample_video.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());
        evidenceId = evidence.getEvidenceId();

        adminToken = JwtTestSupport.loginAndGetToken(mockMvc, "3333", "4444");
        userToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");
    }

    @AfterEach
    void tearDown() {
        custodyLogRepository.deleteAll();
        evidenceRepository.deleteAll();
        inviteCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listEvidences_withAdmin_returnsItems() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evidences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fileName").value("sample_video.mp4"));
    }

    @Test
    void listEvidences_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evidences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEvidence_withAdmin_returnsDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evidences/{evidenceId}", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue").exists())
                .andExpect(jsonPath("$.uploaderUsername").value("1111"));
    }

    @Test
    void deleteEvidence_withAdmin_softDeletesAndRecordsCoC() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/evidences/{evidenceId}", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"테스트 삭제"}
                                """))
                .andExpect(status().isNoContent());

        Evidence deleted = evidenceRepository.findById(evidenceId).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(EvidenceStatus.DELETED);
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(custodyLogRepository.count()).isEqualTo(1);
    }
}
