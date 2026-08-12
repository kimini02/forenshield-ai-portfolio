package com.example.demo.service.report;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.ReportIssueStatus;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportIssueTaskInsertRepository;
import com.example.demo.repository.ReportRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportIssueTaskServiceTest {

    @Mock
    private AnalysisRequestRepository analysisRequestRepository;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportIssueTaskRepository reportIssueTaskRepository;

    @Mock
    private ReportIssueTaskInsertRepository reportIssueTaskInsertRepository;

    private ReportIssueTaskService service;

    @BeforeEach
    void setUp() {
        service = new ReportIssueTaskService(
                analysisRequestRepository,
                analysisResultRepository,
                reportRepository,
                reportIssueTaskRepository,
                reportIssueTaskInsertRepository
        );
    }

    @Test
    void createPendingTasks_createsOneTaskPerEligibleAnalysisResult() {
        CaseProfile profile = mock(CaseProfile.class);
        when(profile.getCaseProfileId()).thenReturn(40L);
        Evidence first = evidence(1L);
        Evidence second = evidence(2L);
        Evidence third = evidence(3L);
        stubEligible(first, 11L, 103L);
        stubEligible(second, 12L, 102L);
        stubEligible(third, 13L, 101L);
        when(reportIssueTaskInsertRepository.insertPendingIfAbsent(
                anyLong(), anyLong(), anyLong(), anyLong(), any())).thenReturn(1);

        int inserted = service.createPendingTasks(profile, List.of(first, second, third), 50L);

        assertThat(inserted).isEqualTo(3);
        var ordered = inOrder(reportIssueTaskInsertRepository);
        ordered.verify(reportIssueTaskInsertRepository)
                .insertPendingIfAbsent(org.mockito.ArgumentMatchers.eq(40L), org.mockito.ArgumentMatchers.eq(3L),
                        org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.eq(50L), any());
        ordered.verify(reportIssueTaskInsertRepository)
                .insertPendingIfAbsent(org.mockito.ArgumentMatchers.eq(40L), org.mockito.ArgumentMatchers.eq(2L),
                        org.mockito.ArgumentMatchers.eq(102L), org.mockito.ArgumentMatchers.eq(50L), any());
        ordered.verify(reportIssueTaskInsertRepository)
                .insertPendingIfAbsent(org.mockito.ArgumentMatchers.eq(40L), org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(103L), org.mockito.ArgumentMatchers.eq(50L), any());
    }

    @Test
    void createPendingTasks_skipsEvidenceWhoseLatestRequestIsNotCompleted() {
        CaseProfile profile = mock(CaseProfile.class);
        Evidence evidence = evidence(1L);
        AnalysisRequest request = request(11L, AnalysisStatus.ANALYZING);
        when(analysisRequestRepository.findTopByEvidenceIdOrderByRequestedAtDesc(1L))
                .thenReturn(Optional.of(request));
        assertThat(service.createPendingTasks(profile, List.of(evidence), 50L)).isZero();
        verify(analysisResultRepository, never()).findByAnalysisRequestId(any());
    }

    @Test
    void createPendingTasks_skipsCompletedRequestWithoutAnalysisResult() {
        CaseProfile profile = mock(CaseProfile.class);
        Evidence evidence = evidence(1L);
        AnalysisRequest request = request(11L, AnalysisStatus.COMPLETED);
        when(analysisRequestRepository.findTopByEvidenceIdOrderByRequestedAtDesc(1L))
                .thenReturn(Optional.of(request));
        when(analysisResultRepository.findByAnalysisRequestId(11L)).thenReturn(Optional.empty());
        assertThat(service.createPendingTasks(profile, List.of(evidence), 50L)).isZero();
    }

    @Test
    void createPendingTasks_skipsAnalysisResultThatAlreadyHasIssuedReport() {
        CaseProfile profile = mock(CaseProfile.class);
        Evidence evidence = evidence(1L);
        AnalysisRequest request = request(11L, AnalysisStatus.COMPLETED);
        AnalysisResult result = result(101L);
        Report issued = mock(Report.class);
        when(issued.isIssued()).thenReturn(true);
        when(analysisRequestRepository.findTopByEvidenceIdOrderByRequestedAtDesc(1L))
                .thenReturn(Optional.of(request));
        when(analysisResultRepository.findByAnalysisRequestId(11L)).thenReturn(Optional.of(result));
        when(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.of(issued));
        assertThat(service.createPendingTasks(profile, List.of(evidence), 50L)).isZero();
        verify(reportIssueTaskInsertRepository, never())
                .insertPendingIfAbsent(anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void aggregate_derivesCaseLevelStatusFromTasks() {
        assertThat(service.aggregate(List.of())).isEqualTo(ReportIssueStatus.NOT_REQUIRED);
        assertThat(service.aggregate(List.of(ReportIssueTaskStatus.PENDING, ReportIssueTaskStatus.FAILED)))
                .isEqualTo(ReportIssueStatus.PENDING);
        assertThat(service.aggregate(List.of(ReportIssueTaskStatus.PROCESSING, ReportIssueTaskStatus.COMPLETED)))
                .isEqualTo(ReportIssueStatus.PROCESSING);
        assertThat(service.aggregate(List.of(ReportIssueTaskStatus.COMPLETED, ReportIssueTaskStatus.COMPLETED)))
                .isEqualTo(ReportIssueStatus.COMPLETED);
        assertThat(service.aggregate(List.of(ReportIssueTaskStatus.FAILED, ReportIssueTaskStatus.FAILED)))
                .isEqualTo(ReportIssueStatus.FAILED);
        assertThat(service.aggregate(List.of(ReportIssueTaskStatus.COMPLETED, ReportIssueTaskStatus.FAILED)))
                .isEqualTo(ReportIssueStatus.PARTIAL_FAILED);
    }

    private void stubEligible(Evidence evidence, Long requestId, Long resultId) {
        AnalysisRequest request = request(requestId, AnalysisStatus.COMPLETED);
        AnalysisResult result = result(resultId);
        when(analysisRequestRepository.findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId()))
                .thenReturn(Optional.of(request));
        when(analysisResultRepository.findByAnalysisRequestId(requestId)).thenReturn(Optional.of(result));
        when(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(resultId)).thenReturn(Optional.empty());
    }

    private Evidence evidence(Long evidenceId) {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(evidenceId);
        return evidence;
    }

    private AnalysisRequest request(Long requestId, AnalysisStatus status) {
        AnalysisRequest request = new AnalysisRequest();
        request.setAnalysisRequestId(requestId);
        request.setStatus(status);
        return request;
    }

    private AnalysisResult result(Long resultId) {
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisResultId(resultId);
        return result;
    }
}
