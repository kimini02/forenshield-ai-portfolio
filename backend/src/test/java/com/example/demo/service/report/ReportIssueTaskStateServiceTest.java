package com.example.demo.service.report;

import com.example.demo.config.ReportIssueWorkerProperties;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.repository.ReportIssueTaskRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportIssueTaskStateServiceTest {

    @Mock
    private ReportIssueTaskRepository repository;

    private ReportIssueWorkerProperties properties;
    private ReportIssueTaskStateService service;

    @BeforeEach
    void setUp() {
        properties = new ReportIssueWorkerProperties();
        properties.setRetryDelayMs(1_000);
        service = new ReportIssueTaskStateService(repository, properties);
    }

    @Test
    void retryOrFail_withAttemptsRemainingSchedulesPendingRetry() {
        ReportIssueTask task = processingTask();
        properties.setMaxAttempts(3);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        service.retryOrFail(1L, new RuntimeException("pdf failed"));

        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.PENDING);
        assertThat(task.getNextRetryAt()).isNotNull();
        assertThat(task.getLastError()).contains("pdf failed");
    }

    @Test
    void retryOrFail_atMaxAttemptsMarksTaskFailed() {
        ReportIssueTask task = processingTask();
        properties.setMaxAttempts(1);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        service.retryOrFail(1L, new RuntimeException("write failed"));

        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.FAILED);
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getLastError()).contains("write failed");
    }

    private ReportIssueTask processingTask() {
        ReportIssueTask task = ReportIssueTask.pending(1L, 2L, 3L, 4L);
        task.claim(LocalDateTime.now());
        return task;
    }
}
