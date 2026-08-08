package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 운영 RDS: {@code ddl-auto=validate} 환경에서 {@code overlay_jobs} 테이블이 없으면
 * 오버레이 생성 API가 500으로 실패합니다. 012 마이그레이션을 기동 시 1회 보정합니다.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class PostgresOverlayJobsSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgresDatabase()) {
            return;
        }

        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS overlay_jobs (
                        overlay_job_id       BIGSERIAL PRIMARY KEY,
                        evidence_id          BIGINT       NOT NULL REFERENCES evidences (evidence_id) ON DELETE CASCADE,
                        analysis_request_id  BIGINT       NOT NULL,
                        module               VARCHAR(40)  NOT NULL,
                        status               VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
                        progress_percent     INTEGER      NOT NULL DEFAULT 0,
                        overlay_video_url    VARCHAR(2000),
                        error_code           VARCHAR(50),
                        error_message        TEXT,
                        requested_by         BIGINT       NOT NULL,
                        requested_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                        started_at           TIMESTAMPTZ,
                        completed_at         TIMESTAMPTZ,
                        CONSTRAINT chk_overlay_jobs_status
                            CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'))
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_overlay_jobs_evidence_module_status
                    ON overlay_jobs (evidence_id, module, status)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_overlay_jobs_requested_at
                    ON overlay_jobs (requested_at DESC)
                    """);
            log.info("Ensured overlay_jobs schema (012) on PostgreSQL");
        } catch (Exception ex) {
            log.error("Failed to ensure overlay_jobs schema: {}", ex.getMessage(), ex);
        }
    }

    private boolean isPostgresDatabase() {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:postgresql:");
    }
}
