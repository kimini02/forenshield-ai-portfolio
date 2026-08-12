package com.example.demo.service.report;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.ReportIssueStatus;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportRepository;
import java.util.ArrayList;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReportIssueTask> createPendingTasks(
            CaseProfile caseProfile,
            List<Evidence> evidences,
            Long requestedBy
    ) {
        List<ReportIssueTask> tasks = new ArrayList<>();
        for (Evidence evidence : evidences) {
            AnalysisRequest request = analysisRequestRepository
                    .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                    .filter(candidate -> candidate.getStatus() == AnalysisStatus.COMPLETED)
                    .orElse(null);
            if (request == null) {
                continue;
            }

            AnalysisResult result = analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId())
                    .orElse(null);
            if (result == null || shouldSkip(result.getAnalysisResultId())) {
                continue;
            }

            tasks.add(ReportIssueTask.pending(
                    caseProfile.getCaseProfileId(),
                    evidence.getEvidenceId(),
                    result.getAnalysisResultId(),
                    requestedBy
            ));
        }
        return reportIssueTaskRepository.saveAllAndFlush(tasks);
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

    private boolean shouldSkip(Long analysisResultId) {
        boolean issued = reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(analysisResultId)
                .map(report -> report.isIssued())
                .orElse(false);
        return issued || reportIssueTaskRepository.existsByAnalysisResultId(analysisResultId);
    }
}
