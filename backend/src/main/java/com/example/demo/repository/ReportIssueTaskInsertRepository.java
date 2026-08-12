package com.example.demo.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Atomic ReportIssueTask creation.
 *
 * <p>PostgreSQL is the production source of truth. H2 uses its standard MERGE form only so the
 * existing H2-based regression suite can exercise the same insert-if-absent contract.</p>
 */
@Repository
public class ReportIssueTaskInsertRepository {

    private static final String POSTGRES_INSERT = """
            INSERT INTO report_issue_tasks (
                case_profile_id,
                evidence_id,
                analysis_result_id,
                requested_by,
                status,
                attempt_count,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
            ON CONFLICT (analysis_result_id) DO NOTHING
            """;

    private static final String H2_INSERT = """
            MERGE INTO report_issue_tasks AS target
            USING (VALUES (
                CAST(? AS BIGINT),
                CAST(? AS BIGINT),
                CAST(? AS BIGINT),
                CAST(? AS BIGINT),
                CAST(? AS TIMESTAMP),
                CAST(? AS TIMESTAMP)
            )) AS source (
                case_profile_id,
                evidence_id,
                analysis_result_id,
                requested_by,
                created_at,
                updated_at
            )
            ON target.analysis_result_id = source.analysis_result_id
            WHEN NOT MATCHED THEN INSERT (
                case_profile_id,
                evidence_id,
                analysis_result_id,
                requested_by,
                status,
                attempt_count,
                created_at,
                updated_at
            ) VALUES (
                source.case_profile_id,
                source.evidence_id,
                source.analysis_result_id,
                source.requested_by,
                'PENDING',
                0,
                source.created_at,
                source.updated_at
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String insertSql;

    public ReportIssueTaskInsertRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.insertSql = resolveInsertSql(dataSource);
    }

    public int insertPendingIfAbsent(
            Long caseProfileId,
            Long evidenceId,
            Long analysisResultId,
            Long requestedBy,
            LocalDateTime createdAt
    ) {
        return jdbcTemplate.update(
                insertSql,
                caseProfileId,
                evidenceId,
                analysisResultId,
                requestedBy,
                createdAt,
                createdAt
        );
    }

    private String resolveInsertSql(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String database = connection.getMetaData().getDatabaseProductName();
            if ("PostgreSQL".equals(database)) {
                return POSTGRES_INSERT;
            }
            if ("H2".equals(database)) {
                return H2_INSERT;
            }
            throw new IllegalStateException("Unsupported database for atomic report task insert: " + database);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to determine database for atomic report task insert", exception);
        }
    }
}
