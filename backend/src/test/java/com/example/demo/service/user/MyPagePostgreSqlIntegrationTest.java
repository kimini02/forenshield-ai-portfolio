package com.example.demo.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.dto.mypage.CaseSummaryResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CaseListQueryRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.custody.RecoveryScoreService;
import com.example.demo.service.evidence.CaseDetailAssembler;
import com.example.demo.service.evidence.CaseEvidencePresentationService;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.service.evidence.EvidenceDetailAssembler;
import com.example.demo.service.evidence.EvidenceDetailService;
import com.example.demo.service.evidence.hls.EvidenceHlsLookupService;
import com.example.demo.service.evidence.hls.EvidenceHlsPlaybackService;
import com.example.demo.service.manifest.EvidenceManifestService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MyPageService.class, CaseListQueryRepository.class, CaseEvidencePresentationService.class})
class MyPagePostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("forenshield_c1")
            .withUsername("forenshield")
            .withPassword("forenshield")
            .withInitScript("db/test/postgresql-domains.sql");

    @Autowired
    private MyPageService myPageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Test
    void groupsByOwnerAndResolvedCaseKey() {
        User firstOwner = saveUser("owner-a", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        User secondOwner = saveUser("owner-b", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        User orgAdmin = saveUser("org-admin", OrgType.POLICE, UserRole.ROLE_ORG_ADMIN);

        saveEvidence(firstOwner, "SAME-CASE", "첫 사건", "first-1.mp4", at(1));
        saveEvidence(firstOwner, "SAME-CASE", "첫 사건", "first-2.mp4", at(2));
        saveEvidence(secondOwner, "SAME-CASE", "둘째 사건", "second.mp4", at(3));

        AnalysisHistoryPageResponse response = history(orgAdmin, "newest", 0, 10, null, null);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent())
                .extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("SAME-CASE", "SAME-CASE");
        assertThat(response.getContent())
                .extracting(CaseSummaryResponse::getCreatedBy)
                .containsExactly(
                        String.valueOf(secondOwner.getUserId()),
                        String.valueOf(firstOwner.getUserId())
                );
        assertThat(response.getContent())
                .extracting(CaseSummaryResponse::getEvidenceCount)
                .containsExactly(1, 2);
    }

    @Test
    void resolvesCaseKeyByCaseNumberThenCaseNameThenEvidenceId() {
        User owner = saveUser("key-owner", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        Evidence numberFirst = saveEvidence(owner, "NUMBER-FIRST", "ignored name", "number.mp4", at(1));
        Evidence nameFallback = saveEvidence(owner, "   ", "NAME-FALLBACK", "name.mp4", at(2));
        Evidence idFallback = saveEvidence(owner, null, null, "id.mp4", at(3));

        AnalysisHistoryPageResponse response = history(owner, "oldest", 0, 10, null, null);

        assertThat(response.getContent()).extracting(CaseSummaryResponse::getCaseId)
                .containsExactly(
                        "NUMBER-FIRST",
                        "NAME-FALLBACK",
                        "EVIDENCE-" + idFallback.getEvidenceId()
                );
        assertThat(numberFirst.getEvidenceId()).isNotNull();
        assertThat(nameFallback.getEvidenceId()).isNotNull();
    }

    @Test
    void keepsProfileOnlyCaseAndDoesNotDuplicateItAfterEvidenceArrives() {
        User owner = saveUser("profile-owner", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        caseProfileRepository.save(new CaseProfile(owner.getUserId(), "PROFILE-ONLY", null));

        AnalysisHistoryPageResponse beforeEvidence = history(owner, "newest", 0, 10, null, null);
        assertThat(beforeEvidence.getTotalElements()).isEqualTo(1);
        assertThat(beforeEvidence.getContent().get(0).getEvidenceCount()).isZero();

        saveEvidence(owner, "PROFILE-ONLY", "프로필 사건", "profile.mp4", at(1));
        AnalysisHistoryPageResponse afterEvidence = history(owner, "newest", 0, 10, null, null);

        assertThat(afterEvidence.getTotalElements()).isEqualTo(1);
        assertThat(afterEvidence.getContent()).hasSize(1);
        assertThat(afterEvidence.getContent().get(0).getEvidenceCount()).isEqualTo(1);
    }

    @Test
    void latestRequestUsesRequestIdDescendingWhenRequestedAtIsEqual() {
        User owner = saveUser("tie-owner", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        Evidence evidence = saveEvidence(owner, "TIE-CASE", "동률 사건", "tie.mp4", at(1));
        LocalDateTime requestedAt = at(10);
        AnalysisRequest older = saveRequest(evidence, owner, AnalysisStatus.ANALYZING, requestedAt);
        AnalysisRequest newer = saveRequest(evidence, owner, AnalysisStatus.FAILED, requestedAt);

        assertThat(newer.getAnalysisRequestId()).isGreaterThan(older.getAnalysisRequestId());
        AnalysisHistoryPageResponse response = history(owner, "newest", 0, 10, null, null);

        assertThat(response.getContent()).singleElement()
                .extracting(CaseSummaryResponse::getStatus)
                .isEqualTo("FAILED");
    }

    @Test
    void appliesInvestigatorReviewerOrganizationAndGlobalScopes() {
        User policeOwner = saveUser("police-owner", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        User otherPoliceOwner = saveUser("police-owner-2", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        User prosecutionOwner = saveUser("prosecution-owner", OrgType.PROSECUTION, UserRole.ROLE_INVESTIGATOR);
        User reviewer = saveUser("reviewer", OrgType.POLICE, UserRole.ROLE_REVIEWER);
        User orgAdmin = saveUser("scope-admin", OrgType.POLICE, UserRole.ROLE_ORG_ADMIN);
        User globalAdmin = saveUser("global-admin", OrgType.ETC, UserRole.ROLE_ADMIN);

        Evidence assigned = saveEvidence(policeOwner, "ASSIGNED", "배정 사건", "assigned.mp4", at(1));
        saveEvidence(otherPoliceOwner, "POLICE-OTHER", "기관 사건", "other.mp4", at(2));
        saveEvidence(prosecutionOwner, "PROSECUTION", "타기관 사건", "outside.mp4", at(3));
        CaseProfile assignedProfile = new CaseProfile(
                policeOwner.getUserId(), "ASSIGNED", assigned.getEvidenceId()
        );
        assignedProfile.assignReviewer(reviewer.getUserId());
        caseProfileRepository.save(assignedProfile);

        assertThat(history(policeOwner, "newest", 0, 10, null, null).getTotalElements()).isEqualTo(1);
        assertThat(history(reviewer, "newest", 0, 10, null, null).getContent())
                .extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("ASSIGNED");
        assertThat(history(orgAdmin, "newest", 0, 10, null, null).getTotalElements()).isEqualTo(2);
        assertThat(history(globalAdmin, "newest", 0, 10, null, null).getTotalElements()).isEqualTo(3);
    }

    @Test
    void organizationAdminDetailUsesUploaderIdWithoutCrossingOwnerScope() {
        User firstOwner = saveUser("detail-owner-a", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        User secondOwner = saveUser("detail-owner-b", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        User outsideOwner = saveUser("detail-outsider", OrgType.PROSECUTION, UserRole.ROLE_INVESTIGATOR);
        User orgAdmin = saveUser("detail-admin", OrgType.POLICE, UserRole.ROLE_ORG_ADMIN);
        Evidence firstEvidence = saveEvidence(firstOwner, "DETAIL-DUP", "첫 상세", "detail-a.mp4", at(1));
        Evidence secondEvidence = saveEvidence(secondOwner, "DETAIL-DUP", "둘째 상세", "detail-b.mp4", at(2));
        saveEvidence(outsideOwner, "DETAIL-DUP", "외부 상세", "detail-out.mp4", at(3));
        caseProfileRepository.saveAndFlush(
                new CaseProfile(firstOwner.getUserId(), "DETAIL-DUP", firstEvidence.getEvidenceId())
        );
        CaseProfile secondProfile = caseProfileRepository.saveAndFlush(
                new CaseProfile(secondOwner.getUserId(), "DETAIL-DUP", secondEvidence.getEvidenceId())
        );
        CaseDetailAssembler assembler = mock(CaseDetailAssembler.class);
        EvidenceHlsLookupService hlsLookupService = mock(EvidenceHlsLookupService.class);
        EvidenceDetailService detailService = detailService(assembler, hlsLookupService);
        CaseDetailResponse expected = CaseDetailResponse.builder()
                .caseId("DETAIL-DUP")
                .createdBy(String.valueOf(secondOwner.getUserId()))
                .evidences(List.of())
                .build();
        when(hlsLookupService.findByEvidenceIds(anyList())).thenReturn(Map.of());
        when(assembler.assemble(
                eq(secondOwner),
                eq("DETAIL-DUP"),
                argThat(items -> items.size() == 1
                        && items.get(0).getEvidenceId().equals(secondEvidence.getEvidenceId())),
                anyList(),
                eq(secondProfile),
                eq(secondOwner),
                eq(Map.of())
        )).thenReturn(expected);

        CaseDetailResponse selected = detailService.getCaseDetail(
                orgAdmin, "DETAIL-DUP", secondOwner.getUserId()
        );

        assertThat(selected.getCreatedBy()).isEqualTo(String.valueOf(secondOwner.getUserId()));
        assertThatThrownBy(() -> detailService.getCaseDetail(orgAdmin, "DETAIL-DUP", (Long) null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UPLOADER_ID_REQUIRED")
                );
        assertThatThrownBy(() -> detailService.getCaseDetail(
                orgAdmin, "DETAIL-DUP", outsideOwner.getUserId()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo("CASE_NOT_FOUND")
        );
    }

    @Test
    void pagesAndFiltersInDatabaseWhilePreservingPageContract() {
        User owner = saveUser("page-owner", OrgType.POLICE, UserRole.ROLE_INVESTIGATOR);
        Evidence oldest = saveEvidence(owner, "CASE-1", "alpha oldest", "one.mp4", at(1));
        Evidence processing = saveEvidence(owner, "CASE-2", "bravo processing", "two.mp4", at(2));
        Evidence failed = saveEvidence(owner, "CASE-3", "charlie failed", "three.mp4", at(3));
        saveEvidence(owner, "CASE-4", "delta pending", "four.mp4", at(4));
        Evidence completed = saveEvidence(owner, "CASE-5", "echo completed", "five.mp4", at(5));
        saveRequest(processing, owner, AnalysisStatus.ANALYZING, at(20));
        saveRequest(failed, owner, AnalysisStatus.FAILED, at(21));
        saveRequest(completed, owner, AnalysisStatus.COMPLETED, at(22));

        AnalysisHistoryPageResponse first = history(owner, "newest", 0, 2, null, null);
        AnalysisHistoryPageResponse middle = history(owner, "newest", 1, 2, null, null);
        AnalysisHistoryPageResponse last = history(owner, "newest", 2, 2, null, null);

        assertThat(first.getContent()).extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("CASE-5", "CASE-4");
        assertThat(middle.getContent()).extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("CASE-3", "CASE-2");
        assertThat(last.getContent()).extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("CASE-1");
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getPage()).isZero();
        assertThat(first.getSize()).isEqualTo(2);

        assertThat(history(owner, "oldest", 0, 2, null, null).getContent())
                .extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("CASE-1", "CASE-2");
        assertThat(history(owner, "newest", 0, 10, null, "CHARLIE").getContent())
                .extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("CASE-3");
        assertThat(history(owner, "newest", 0, 10, null, "no-hit").getTotalElements()).isZero();
        assertThat(history(owner, "newest", 0, 10, "FAILED", null).getContent())
                .extracting(CaseSummaryResponse::getCaseId)
                .containsExactly("CASE-3");
        assertThat(history(owner, "status", 0, 10, null, null).getContent())
                .extracting(CaseSummaryResponse::getStatus)
                .containsExactly("PROCESSING", "PENDING", "PENDING", "FAILED", "COMPLETED");
        assertThat(oldest.getEvidenceId()).isNotNull();
    }

    private AnalysisHistoryPageResponse history(
            User user,
            String sort,
            int page,
            int size,
            String status,
            String keyword
    ) {
        return myPageService.getAnalysisHistory(user, sort, page, size, status, keyword);
    }

    private EvidenceDetailService detailService(
            CaseDetailAssembler assembler,
            EvidenceHlsLookupService hlsLookupService
    ) {
        return new EvidenceDetailService(
                evidenceRepository,
                caseProfileRepository,
                userRepository,
                mock(EvidenceAccessService.class),
                mock(EvidenceMetadataRepository.class),
                analysisRequestRepository,
                analysisResultRepository,
                mock(AnalysisModuleResultRepository.class),
                mock(CustodyLogRepository.class),
                mock(EvidenceManifestService.class),
                mock(RecoveryScoreService.class),
                assembler,
                mock(EvidenceDetailAssembler.class),
                hlsLookupService,
                mock(EvidenceHlsPlaybackService.class)
        );
    }

    private User saveUser(String loginId, OrgType organization, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .loginId(loginId)
                .email(loginId + "@test.local")
                .password("encoded")
                .name(loginId)
                .organizationType(organization)
                .department("test")
                .role(role)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
    }

    private Evidence saveEvidence(
            User owner,
            String caseKey,
            String caseName,
            String fileName,
            LocalDateTime uploadedAt
    ) {
        return evidenceRepository.saveAndFlush(Evidence.builder()
                .uploaderId(owner.getUserId())
                .caseNumber(caseKey)
                .caseName(caseName)
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(10L)
                .hashAlgorithm("SHA-256")
                .originalHashValue("a".repeat(64))
                .originalStoragePath("test/" + fileName)
                .uploadedAt(uploadedAt)
                .build());
    }

    private AnalysisRequest saveRequest(
            Evidence evidence,
            User requester,
            AnalysisStatus status,
            LocalDateTime requestedAt
    ) {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(requester.getUserId());
        request.setStatus(status);
        request.setRequestedAt(requestedAt);
        return analysisRequestRepository.saveAndFlush(request);
    }

    private LocalDateTime at(int minute) {
        return LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(minute);
    }
}
