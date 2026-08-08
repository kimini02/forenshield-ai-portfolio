package com.example.demo.controller;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmptyCaseRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
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
    }

    @AfterEach
    void tearDown() {
        caseProfileRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateEmptyCase() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseName":"empty-case"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("empty-case"))
                .andExpect(jsonPath("$.caseName").value("empty-case"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.evidences", hasSize(0)));

        mockMvc.perform(get("/api/v1/cases?caseKey=empty-case")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidences", hasSize(0)))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldIncludeEmptyCaseInAnalysisHistory() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseName":"history-case"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/mypage/analysis-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].caseId").value("history-case"))
                .andExpect(jsonPath("$.content[0].evidenceCount").value(0))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void shouldRejectDuplicateCaseName() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseName":"dup-case"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseName":"dup-case"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_CASE_NAME"));
    }
}
