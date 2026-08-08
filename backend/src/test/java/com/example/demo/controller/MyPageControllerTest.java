package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class MyPageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EvidenceRepository evidenceRepository;

	@Autowired
	private AnalysisRequestRepository analysisRequestRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String userToken;
	private User testUser;

	@BeforeEach
	void setUp() throws Exception {
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
	}

	@AfterEach
	void tearDown() {
		analysisRequestRepository.deleteAll();
		evidenceRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void getAnalysisHistory_withoutAuth_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/mypage/analysis-history"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getAnalysisHistory_withDemoUser_returnsEmptyList() throws Exception {
		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void getAnalysisHistory_withoutCaseNumber_usesCaseNameAsCaseId() throws Exception {
		Evidence evidence = saveEvidenceWithAnalysis("12121212", "case-name-only.mp4", AnalysisStatus.QUEUED);

		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].caseId").value("12121212"))
				.andExpect(jsonPath("$.content[0].caseId").value(not(String.valueOf(evidence.getEvidenceId()))))
				.andExpect(jsonPath("$.content[0].caseName").value("12121212"))
				.andExpect(jsonPath("$.content[0].representativeFileName").value("case-name-only.mp4"))
				.andExpect(jsonPath("$.content[0].evidenceCount").value(1))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"));

		mockMvc.perform(get("/api/v1/cases")
						.param("caseKey", "12121212")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caseId").value("12121212"))
				.andExpect(jsonPath("$.evidences[0].evidenceId").value(evidence.getEvidenceId()));
	}

	@Test
	void getAnalysisHistory_withStatusFilter_returnsMatchingCasesOnly() throws Exception {
		saveEvidenceWithAnalysis("pending-case", "pending.mp4", AnalysisStatus.QUEUED);
		saveEvidenceWithAnalysis("completed-case", "completed.mp4", AnalysisStatus.COMPLETED);
		saveEvidenceWithAnalysis("failed-case", "failed.mp4", AnalysisStatus.FAILED);

		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.param("status", "COMPLETED")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].caseName").value("completed-case"))
				.andExpect(jsonPath("$.content[0].status").value("COMPLETED"));

		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.param("status", "ALL")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void getAnalysisHistory_withQueryFilter_returnsMatchingCasesOnly() throws Exception {
		saveEvidenceWithAnalysis("가짜 영상 사건", "fake.mp4", AnalysisStatus.QUEUED);
		saveEvidenceWithAnalysis("정상 영상 사건", "real.mp4", AnalysisStatus.QUEUED);
		Evidence matchedById = saveEvidenceWithAnalysis("검색용 사건", "search.mp4", AnalysisStatus.QUEUED);

		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.param("q", "가짜")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].caseName").value("가짜 영상 사건"));

		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.param("q", "evd-" + matchedById.getEvidenceId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].caseName").value("검색용 사건"))
				.andExpect(jsonPath("$.content[0].representativeEvidenceId").value(matchedById.getEvidenceId()));

		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.param("q", "")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void getCaseDetail_withEncodedKoreanCaseKey_returnsCase() throws Exception {
		String caseName = "2026-서울-0123 딥페이크 유포 사건";
		Evidence evidence = evidenceRepository.save(Evidence.builder()
				.uploaderId(testUser.getUserId())
				.caseName(caseName)
				.fileName("korean-case.mp4")
				.fileType(FileType.VIDEO)
				.mimeType("video/mp4")
				.fileSize(12L)
				.hashAlgorithm("SHA-256")
				.originalHashValue("b".repeat(64))
				.originalStoragePath("uploads/test/korean-case.mp4")
				.uploadedAt(LocalDateTime.now())
				.build());

		AnalysisRequest request = new AnalysisRequest();
		request.setEvidenceId(evidence.getEvidenceId());
		request.setRequestedBy(testUser.getUserId());
		request.setStatus(AnalysisStatus.QUEUED);
		request.setRequestedAt(LocalDateTime.now());
		analysisRequestRepository.save(request);

		String encodedOnce = java.net.URLEncoder.encode(caseName, java.nio.charset.StandardCharsets.UTF_8);
		String doubleEncoded = java.net.URLEncoder.encode(encodedOnce, java.nio.charset.StandardCharsets.UTF_8);

		mockMvc.perform(get("/api/v1/cases")
						.param("caseKey", doubleEncoded)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caseId").value(caseName))
				.andExpect(jsonPath("$.caseName").value(caseName))
				.andExpect(jsonPath("$.evidences[0].evidenceId").value(evidence.getEvidenceId()));
	}

	@Test
	void getCaseDetail_withoutCaseKey_returnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/cases")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("사건 식별자가 필요합니다."));
	}

	@Test
	void getMyProfile_withDemoUser_returnsProfile() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("1111"))
				.andExpect(jsonPath("$.email").value("1111@local.dev"));
	}

	@Test
	void updateMyProfile_cannotChangeDepartment() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "loginId": "1111",
								  "department": "변경된부서",
								  "currentPassword": "2222"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.department").value("로컬개발팀"));

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(jsonPath("$.department").value("로컬개발팀"));
	}

	@Test
	void updateMyProfile_withWrongPassword_returnsBadRequest() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "loginId": "1111",
								  "currentPassword": "wrong-password"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD"));
	}

	private Evidence saveEvidenceWithAnalysis(String caseName, String fileName, AnalysisStatus analysisStatus) {
		Evidence evidence = evidenceRepository.save(Evidence.builder()
				.uploaderId(testUser.getUserId())
				.caseName(caseName)
				.fileName(fileName)
				.fileType(FileType.VIDEO)
				.mimeType("video/mp4")
				.fileSize(12L)
				.hashAlgorithm("SHA-256")
				.originalHashValue(String.format("%064x", Math.floorMod(caseName.hashCode(), Integer.MAX_VALUE)))
				.originalStoragePath("uploads/test/" + fileName)
				.uploadedAt(LocalDateTime.now())
				.build());

		AnalysisRequest request = new AnalysisRequest();
		request.setEvidenceId(evidence.getEvidenceId());
		request.setRequestedBy(testUser.getUserId());
		request.setStatus(analysisStatus);
		request.setRequestedAt(LocalDateTime.now());
		analysisRequestRepository.save(request);
		return evidence;
	}
}
