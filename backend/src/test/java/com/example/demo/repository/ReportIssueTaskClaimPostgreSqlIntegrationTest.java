package com.example.demo.repository;

import com.example.demo.domain.ReportIssueTask;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportIssueTaskClaimPostgreSqlIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("report_issue_claim")
            .withUsername("forenshield")
            .withPassword("forenshield")
            .withInitScript("db/test/postgresql-domains.sql");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ReportIssueTaskRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAndFlush(ReportIssueTask.pending(1L, 2L, 3L, 4L));
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void skipLocked_allowsExactlyOneWorkerToClaimTheSameTask() throws Exception {
        CountDownLatch workerALocked = new CountDownLatch(1);
        CountDownLatch releaseWorkerA = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var workerA = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                List<ReportIssueTask> tasks = repository.findClaimableForUpdate(1);
                workerALocked.countDown();
                await(releaseWorkerA);
                tasks.forEach(task -> task.claim(LocalDateTime.now()));
                return tasks.stream().map(ReportIssueTask::getReportIssueTaskId).toList();
            }));
            assertThat(workerALocked.await(5, TimeUnit.SECONDS)).isTrue();

            var workerB = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status ->
                    repository.findClaimableForUpdate(1).stream()
                            .map(ReportIssueTask::getReportIssueTaskId)
                            .toList()));

            List<Long> workerBClaims = workerB.get(5, TimeUnit.SECONDS);
            releaseWorkerA.countDown();
            List<Long> workerAClaims = workerA.get(5, TimeUnit.SECONDS);

            assertThat(workerAClaims).hasSize(1);
            assertThat(workerBClaims).isEmpty();
        } finally {
            releaseWorkerA.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent claim test");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
