package com.example.demo.controller;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EvidenceAccessAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;
    private User otherUser;
    private String ownerToken;
    private String otherUserToken;
    private Evidence ownedEvidence;

    @BeforeEach
    void setUp() throws Exception {
        custodyLogRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(User.builder()
                .loginId("inv30")
                .email("inv30@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("분석관")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        otherUser = userRepository.save(User.builder()
                .loginId("inv31")
                .email("inv31@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("다른분석관")
                .organizationType(OrgType.POLICE)
                .department("수사1팀")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        ownerToken = JwtTestSupport.loginAndGetToken(mockMvc, "inv30", "pass1111");
        otherUserToken = JwtTestSupport.loginAndGetToken(mockMvc, "inv31", "pass1111");
        ownedEvidence = saveEvidence(owner, "audit-case", "owned.mp4");
    }

    @AfterEach
    void tearDown() {
        custodyLogRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerRecordsEvidenceViewEvent() throws Exception {
        mockMvc.perform(post("/api/v1/evidences/{evidenceId}/access-events", ownedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "VIEW",
                                  "caseKey": "audit-case",
                                  "source": "protected_video_player"
                                }
                                """))
                .andExpect(status().isNoContent());

        List<CustodyLog> logs = custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                CustodyTargetType.EVIDENCE,
                ownedEvidence.getEvidenceId()
        );
        assertThat(logs).hasSize(1);
        CustodyLog log = logs.get(0);
        assertThat(log.getActionType()).isEqualTo("EVIDENCE_VIEWED");
        assertThat(log.getActorId()).isEqualTo(owner.getUserId());
        assertThat(log.getEventPayloadJson()).contains("protected_video_player");
        assertThat(log.getEventPayloadJson()).contains("audit-case");
        assertThat(log.getCurrentLogHash()).isNotBlank();
    }

    @Test
    void ownerRecordsCaptureAttemptEvent() throws Exception {
        mockMvc.perform(post("/api/v1/evidences/{evidenceId}/access-events", ownedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "CAPTURE_ATTEMPT",
                                  "source": "protected_video_player"
                                }
                                """))
                .andExpect(status().isNoContent());

        List<CustodyLog> logs = custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                CustodyTargetType.EVIDENCE,
                ownedEvidence.getEvidenceId()
        );
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getActionType()).isEqualTo("EVIDENCE_CAPTURE_ATTEMPTED");
    }

    @Test
    void nonOwnerCannotRecordEvent() throws Exception {
        mockMvc.perform(post("/api/v1/evidences/{evidenceId}/access-events", ownedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"VIEW"}
                                """))
                .andExpect(status().isNotFound());

        assertThat(custodyLogRepository.count()).isZero();
    }

    @Test
    void rejectsMissingEventType() throws Exception {
        mockMvc.perform(post("/api/v1/evidences/{evidenceId}/access-events", ownedEvidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(custodyLogRepository.count()).isZero();
    }

    private Evidence saveEvidence(User uploader, String caseName, String fileName) {
        return evidenceRepository.save(Evidence.builder()
                .uploaderId(uploader.getUserId())
                .caseName(caseName)
                .caseNumber(caseName)
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm("SHA-256")
                .originalHashValue("e".repeat(64))
                .originalStoragePath("uploads/test/" + fileName)
                .uploadedAt(LocalDateTime.now())
                .build());
    }
}
