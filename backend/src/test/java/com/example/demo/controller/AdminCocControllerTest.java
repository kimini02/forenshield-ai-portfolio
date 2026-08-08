package com.example.demo.controller;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.support.AbstractEvidenceIntegrationTest;
import com.example.demo.support.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCocControllerTest extends AbstractEvidenceIntegrationTest {

    @Autowired
    private CustodyLogService custodyLogService;

    private String adminToken;
    private Evidence evidence;

    @BeforeEach
    void setUpAdminCoc() throws Exception {
        User admin = userRepository.save(User.builder()
                .loginId("coc-admin")
                .email("coc-admin@test.local")
                .password(passwordEncoder.encode("pass1234"))
                .name("CoC 관리자")
                .organizationType(OrgType.ETC)
                .department("감사팀")
                .role(UserRole.ROLE_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
        adminToken = JwtTestSupport.loginAndGetToken(mockMvc, "coc-admin", "pass1234");
        evidence = saveVideoEvidence(admin, "coc-evidence.mp4");
        custodyLogService.recordEvidenceAction(admin, evidence, "EVIDENCE_UPLOADED", "증거 등록");
    }

    @Test
    void listChains_returnsEvidenceTimelineAndVerificationStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/coc/chains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.validCount").value(1))
                .andExpect(jsonPath("$.brokenCount").value(0))
                .andExpect(jsonPath("$.chains[0].evidenceId").value(evidence.getEvidenceId()))
                .andExpect(jsonPath("$.chains[0].status").value("VALID"))
                .andExpect(jsonPath("$.chains[0].events[0].eventType").value("EVIDENCE_UPLOADED"))
                .andExpect(jsonPath("$.chains[0].events[0].chainValid").value(true));
    }
}
