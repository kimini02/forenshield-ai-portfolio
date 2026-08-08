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
 * H2 로컬/테스트: 레거시 prototype 테이블 제거 및 ERD v3 부분 인덱스 보완.
 */
@Slf4j
@Component
@Profile({"local", "default", "test"})
@RequiredArgsConstructor
public class H2ErdSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!isH2Database()) {
            return;
        }

        jdbcTemplate.execute("DROP TABLE IF EXISTS evidence");

        try {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_analysis_requests_one_completed
                    ON analysis_requests (evidence_id)
                    WHERE status = 'COMPLETED'
                    """);
        } catch (Exception e) {
            log.warn("Could not create partial unique index on analysis_requests: {}", e.getMessage());
        }

        migrateReportsTableForComparePdf();
        migrateReportsPublicVerification();
        migrateReportPublicationLifecycle();
        migrateReportPublicationSnapshots();
        migrateEvidencesV2Workflow();
        migrateEvidenceMetadataReadiness();
        migrateBlockchainAnchorAnalysisSnapshot();
    }

    void migrateEvidencesV2Workflow() {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE evidences
                    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL
                    """);
        } catch (Exception e) {
            log.warn("evidences.lifecycle_status migration skipped: {}", e.getMessage());
        }
        addColumnIfMissing("evidences", "evidence_role", "VARCHAR(20)");
        addColumnIfMissing("evidences", "display_label", "VARCHAR(100)");
        addColumnIfMissing("evidences", "replacement_evidence_id", "BIGINT");
        addColumnIfMissing("evidences", "excluded_reason", "VARCHAR(500)");
    }

    void migrateEvidenceMetadataReadiness() {
        addColumnIfMissing("evidence_metadata", "readiness_json", "JSON");
    }

    void migrateBlockchainAnchorAnalysisSnapshot() {
        addColumnIfMissing("blockchain_anchors", "analysis_model_json", "CLOB");
        addColumnIfMissing("blockchain_anchors", "analysis_modules_json", "CLOB");
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s %s".formatted(table, column, type));
        } catch (Exception e) {
            log.warn("{}.{} migration skipped: {}", table, column, e.getMessage());
        }
    }

    /**
     * compare PDF는 analysis_result_id 없이 reports에 저장됩니다.
     * 레거시 H2 스키마(NOT NULL)를 PostgreSQL 마이그레이션(002)과 동일하게 완화합니다.
     */
    void migrateReportsTableForComparePdf() {
        try {
            jdbcTemplate.execute("ALTER TABLE reports ALTER COLUMN analysis_result_id DROP NOT NULL");
        } catch (Exception e) {
            log.debug("reports.analysis_result_id already nullable or table missing: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS compare_id BIGINT");
        } catch (Exception e) {
            log.debug("reports.compare_id migration skipped: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_reports_compare_id ON reports (compare_id)");
        } catch (Exception e) {
            log.warn("Could not create idx_reports_compare_id: {}", e.getMessage());
        }
    }

    /**
     * PDF 진위 확인/외부 열람 기능에서 사용하는 reports 확장 컬럼.
     * PostgreSQL은 006_report_public_verification.sql로 반영하고, H2 로컬 DB는 여기서 보정합니다.
     */
    void migrateReportsPublicVerification() {
        addColumnIfMissing("reports", "report_no", "VARCHAR(40)");
        addColumnIfMissing("reports", "verification_token", "VARCHAR(100)");
        addColumnIfMissing("reports", "verification_code", "VARCHAR(30)");
        addColumnIfMissing("reports", "public_access_code", "VARCHAR(30)");
        addColumnIfMissing("reports", "public_access_enabled", "BOOLEAN DEFAULT FALSE NOT NULL");
        addColumnIfMissing("reports", "public_access_issued_at", "TIMESTAMP");
        addColumnIfMissing("reports", "public_access_expires_at", "TIMESTAMP");

        createIndexIfMissing("ux_reports_report_no", "reports", "report_no", true);
        createIndexIfMissing("ux_reports_verification_token", "reports", "verification_token", true);
        createIndexIfMissing("ux_reports_verification_code", "reports", "verification_code", true);
        createIndexIfMissing("ux_reports_public_access_code", "reports", "public_access_code", true);
    }

    void migrateReportPublicationLifecycle() {
        addColumnIfMissing("reports", "publication_status", "VARCHAR(20) DEFAULT 'ISSUED' NOT NULL");
        addColumnIfMissing("reports", "report_version", "INTEGER DEFAULT 1 NOT NULL");
        addColumnIfMissing("reports", "issued_by", "BIGINT");
        addColumnIfMissing("reports", "issued_at", "TIMESTAMP");
        addColumnIfMissing("reports", "superseded_at", "TIMESTAMP");
        addColumnIfMissing("case_profiles", "review_approved_at", "TIMESTAMP");
        createIndexIfMissing("idx_reports_publication_status", "reports", "publication_status", false);
    }

    void migrateReportPublicationSnapshots() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS report_publication_snapshots (
                        snapshot_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        report_id BIGINT NOT NULL,
                        schema_version VARCHAR(20) NOT NULL,
                        pdf_template_version VARCHAR(40) NOT NULL,
                        report_input_json CLOB NOT NULL,
                        public_summary_json CLOB NOT NULL,
                        artifact_manifest_json CLOB NOT NULL,
                        display_policy_json CLOB NOT NULL,
                        snapshot_hash VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        CONSTRAINT fk_report_publication_snapshots_report
                            FOREIGN KEY (report_id) REFERENCES reports (report_id),
                        CONSTRAINT ux_report_publication_snapshots_report UNIQUE (report_id)
                    )
                    """);
            createIndexIfMissing(
                    "idx_report_publication_snapshots_created_at",
                    "report_publication_snapshots",
                    "created_at",
                    false
            );
        } catch (Exception e) {
            log.warn("report_publication_snapshots migration skipped: {}", e.getMessage());
        }
    }

    private void createIndexIfMissing(String index, String table, String column, boolean unique) {
        try {
            jdbcTemplate.execute(
                    "CREATE %s INDEX IF NOT EXISTS %s ON %s (%s)"
                            .formatted(unique ? "UNIQUE" : "", index, table, column));
        } catch (Exception e) {
            log.warn("{}.{} index migration skipped: {}", table, column, e.getMessage());
        }
    }

    private boolean isH2Database() {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:h2:");
    }
}
