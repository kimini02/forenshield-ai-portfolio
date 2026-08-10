package com.example.demo.service.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.custody.RecoveryScoreService;
import com.example.demo.service.evidence.hls.EvidenceHlsLookupService;
import com.example.demo.service.evidence.hls.EvidenceHlsPlaybackService;
import com.example.demo.service.manifest.EvidenceManifestService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvidenceDetailOwnerScopeTest {

    @Mock
    private EvidenceRepository evidenceRepository;
    @Mock
    private CaseProfileRepository caseProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EvidenceAccessService evidenceAccessService;
    @Mock
    private EvidenceMetadataRepository evidenceMetadataRepository;
    @Mock
    private AnalysisRequestRepository analysisRequestRepository;
    @Mock
    private AnalysisResultRepository analysisResultRepository;
    @Mock
    private AnalysisModuleResultRepository analysisModuleResultRepository;
    @Mock
    private CustodyLogRepository custodyLogRepository;
    @Mock
    private EvidenceManifestService evidenceManifestService;
    @Mock
    private RecoveryScoreService recoveryScoreService;
    @Mock
    private CaseDetailAssembler caseDetailAssembler;
    @Mock
    private EvidenceDetailAssembler evidenceDetailAssembler;
    @Mock
    private EvidenceHlsLookupService evidenceHlsLookupService;
    @Mock
    private EvidenceHlsPlaybackService evidenceHlsPlaybackService;

    @InjectMocks
    private EvidenceDetailService evidenceDetailService;

    @Test
    void reviewerMustSpecifyOwnerWhenSameCaseKeyIsAssignedFromTwoOwners() {
        User reviewer = reviewer(90L);
        when(caseProfileRepository.findByReviewerId(90L)).thenReturn(List.of(
                assignedProfile(1L, "DUPLICATE", 90L),
                assignedProfile(2L, "DUPLICATE", 90L)
        ));

        assertThatThrownBy(() -> evidenceDetailService.getCaseDetail(reviewer, "DUPLICATE", (Long) null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("UPLOADER_ID_REQUIRED")
                );
    }

    @Test
    void reviewerUploaderIdSelectsOnlyTheAssignedOwner() {
        User reviewer = reviewer(90L);
        User selectedOwner = org.mockito.Mockito.mock(User.class);
        CaseProfile first = assignedProfile(1L, "DUPLICATE", 90L);
        CaseProfile second = assignedProfile(2L, "DUPLICATE", 90L);
        CaseDetailResponse expected = CaseDetailResponse.builder()
                .caseId("DUPLICATE")
                .createdBy("2")
                .evidences(List.of())
                .build();
        when(caseProfileRepository.findByReviewerId(90L)).thenReturn(List.of(first, second));
        when(userRepository.findByUserIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(selectedOwner));
        when(evidenceRepository.findByUploaderIdAndCaseKey(2L, "DUPLICATE")).thenReturn(List.of());
        when(caseDetailAssembler.assembleEmptyCase("DUPLICATE", second, selectedOwner)).thenReturn(expected);

        CaseDetailResponse actual = evidenceDetailService.getCaseDetail(reviewer, "DUPLICATE", 2L);

        assertThat(actual).isSameAs(expected);
        verify(evidenceRepository).findByUploaderIdAndCaseKey(2L, "DUPLICATE");
        verify(evidenceRepository, never()).findByUploaderIdAndCaseKey(1L, "DUPLICATE");
    }

    @Test
    void reviewerCannotUseUploaderIdForAnUnassignedOwner() {
        User reviewer = reviewer(90L);
        when(caseProfileRepository.findByReviewerId(90L)).thenReturn(List.of(
                assignedProfile(1L, "DUPLICATE", 90L)
        ));

        assertThatThrownBy(() -> evidenceDetailService.getCaseDetail(reviewer, "DUPLICATE", 2L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("CASE_NOT_FOUND")
                );
        verify(userRepository, never()).findByUserIdAndDeletedAtIsNull(2L);
    }

    private User reviewer(Long reviewerId) {
        User reviewer = org.mockito.Mockito.mock(User.class);
        when(reviewer.getUserId()).thenReturn(reviewerId);
        when(reviewer.getRole()).thenReturn(UserRole.ROLE_REVIEWER);
        return reviewer;
    }

    private CaseProfile assignedProfile(Long uploaderId, String caseKey, Long reviewerId) {
        CaseProfile profile = new CaseProfile(uploaderId, caseKey, null);
        profile.assignReviewer(reviewerId);
        return profile;
    }
}
