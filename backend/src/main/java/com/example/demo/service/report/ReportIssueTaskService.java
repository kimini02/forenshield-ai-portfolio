package com.example.demo.service.report;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.ReportIssueStatus;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportIssueTaskInsertRepository;
import com.example.demo.repository.ReportRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportIssueTaskService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final ReportRepository reportRepository;
    private final ReportIssueTaskRepository reportIssueTaskRepository;
    private final ReportIssueTaskInsertRepository reportIssueTaskInsertRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int createPendingTasks(
            CaseProfile caseProfile,
            List<Evidence> evidences,
            Long requestedBy
    ) {
        List<TaskCandidate> candidates = evidences.stream()
                .map(this::resolveCandidate)
                .filter(candidate -> candidate != null)
                .filter(candidate -> !hasIssuedReport(candidate.analysisResultId()))
                .sorted(Comparator.comparing(TaskCandidate::analysisResultId))
                .toList();

        int inserted = 0;
        for (TaskCandidate candidate : candidates) {
            inserted += reportIssueTaskInsertRepository.insertPendingIfAbsent(
                    caseProfile.getCaseProfileId(),
                    candidate.evidenceId(),
                    candidate.analysisResultId(),
                    requestedBy,
                    LocalDateTime.now()
            );
        }
        return inserted;
    }

    @Transactional(readOnly = true)
    public ReportIssueStatus resolveCaseStatus(Long caseProfileId) {
        if (caseProfileId == null) {
            return ReportIssueStatus.NOT_REQUIRED;
        }
        return aggregate(reportIssueTaskRepository.findStatusesByCaseProfileId(caseProfileId));
    }

    ReportIssueStatus aggregate(List<ReportIssueTaskStatus> statuses) {
        if (statuses.isEmpty()) {
            return ReportIssueStatus.NOT_REQUIRED;
        }
        if (statuses.contains(ReportIssueTaskStatus.PROCESSING)) {
            return ReportIssueStatus.PROCESSING;
        }
        if (statuses.contains(ReportIssueTaskStatus.PENDING)) {
            return ReportIssueStatus.PENDING;
        }

        boolean hasCompleted = statuses.contains(ReportIssueTaskStatus.COMPLETED);
        boolean hasFailed = statuses.contains(ReportIssueTaskStatus.FAILED);
        if (hasCompleted && hasFailed) {
            return ReportIssueStatus.PARTIAL_FAILED;
        }
        return hasCompleted ? ReportIssueStatus.COMPLETED : ReportIssueStatus.FAILED;
    }

    private TaskCandidate resolveCandidate(Evidence evidence) {
        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                .filter(candidate -> candidate.getStatus() == AnalysisStatus.COMPLETED)
                .orElse(null);
        if (request == null) {
            return null;
        }
        return analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId())
                .map(result -> new TaskCandidate(evidence.getEvidenceId(), result.getAnalysisResultId()))
                .orElse(null);
    }

    private boolean hasIssuedReport(Long analysisResultId) {
        return reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(analysisResultId)
                .map(report -> report.isIssued())
                .orElse(false);
    }

    private record TaskCandidate(Long evidenceId, Long analysisResultId) {
    }
}
