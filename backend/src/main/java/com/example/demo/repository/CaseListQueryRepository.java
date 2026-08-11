package com.example.demo.repository;

import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.util.UserRoleSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CaseListQueryRepository {

    private static final String RESOLVED_CASE_KEY = """
            CASE
                WHEN e.case_number IS NOT NULL AND TRIM(e.case_number) <> '' THEN e.case_number
                WHEN e.case_name IS NOT NULL AND TRIM(e.case_name) <> '' THEN e.case_name
                ELSE CONCAT('EVIDENCE-', e.evidence_id)
            END
            """;

    private static final String CASE_CANDIDATES_CTE = """
            WITH latest_request_ranked AS (
                SELECT ar.evidence_id,
                       ar.status,
                       ROW_NUMBER() OVER (
                           PARTITION BY ar.evidence_id
                           ORDER BY ar.requested_at DESC, ar.analysis_request_id DESC
                       ) AS request_rank
                FROM analysis_requests ar
            ),
            case_evidence AS (
                SELECT e.evidence_id,
                       e.uploader_id AS owner_id,
                       %s AS case_key,
                       e.case_name,
                       e.display_label,
                       e.lifecycle_status,
                       e.uploaded_at,
                       CASE
                           WHEN lr.status = 'ANALYZING' THEN 0
                           WHEN lr.status IS NULL OR lr.status = 'QUEUED' THEN 1
                           WHEN lr.status = 'FAILED' THEN 2
                           ELSE 3
                       END AS status_priority
                FROM evidences e
                LEFT JOIN latest_request_ranked lr
                  ON lr.evidence_id = e.evidence_id
                 AND lr.request_rank = 1
                WHERE e.status = 'UPLOADED'
                  AND e.deleted_at IS NULL
            ),
            positioned_evidence AS (
                SELECT ce.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY ce.owner_id, ce.case_key
                           ORDER BY ce.evidence_id
                       ) AS evidence_position
                FROM case_evidence ce
            ),
            evidence_aggregates AS (
                SELECT owner_id,
                       case_key,
                       MIN(uploaded_at) AS created_at,
                       MIN(status_priority) AS status_priority
                FROM positioned_evidence
                GROUP BY owner_id, case_key
            ),
            representative_ranked AS (
                SELECT pe.owner_id,
                       pe.case_key,
                       pe.evidence_id,
                       pe.case_name,
                       pe.display_label,
                       pe.evidence_position,
                       ROW_NUMBER() OVER (
                           PARTITION BY pe.owner_id, pe.case_key
                           ORDER BY
                               CASE
                                   WHEN cp.representative_evidence_id = pe.evidence_id THEN 0
                                   WHEN pe.lifecycle_status = 'ACTIVE' THEN 1
                                   ELSE 2
                               END,
                               pe.evidence_id
                       ) AS representative_rank
                FROM positioned_evidence pe
                LEFT JOIN case_profiles cp
                  ON cp.uploader_id = pe.owner_id
                 AND cp.case_key = pe.case_key
            ),
            evidence_candidates AS (
                SELECT ea.owner_id,
                       ea.case_key,
                       ea.created_at,
                       ea.status_priority,
                       CASE ea.status_priority
                           WHEN 0 THEN 'PROCESSING'
                           WHEN 1 THEN 'PENDING'
                           WHEN 2 THEN 'FAILED'
                           ELSE 'COMPLETED'
                       END AS case_status,
                       COALESCE(NULLIF(TRIM(rr.case_name), ''), ea.case_key) AS search_case_name,
                       CASE
                           WHEN rr.display_label IS NOT NULL AND TRIM(rr.display_label) <> ''
                               THEN rr.display_label
                           ELSE CONCAT('증거 ', rr.evidence_position)
                       END AS representative_label,
                       rr.evidence_id AS representative_evidence_id,
                       cp.reviewer_id
                FROM evidence_aggregates ea
                JOIN representative_ranked rr
                  ON rr.owner_id = ea.owner_id
                 AND rr.case_key = ea.case_key
                 AND rr.representative_rank = 1
                LEFT JOIN case_profiles cp
                  ON cp.uploader_id = ea.owner_id
                 AND cp.case_key = ea.case_key
            ),
            profile_candidates AS (
                SELECT cp.uploader_id AS owner_id,
                       cp.case_key,
                       cp.updated_at AS created_at,
                       1 AS status_priority,
                       'PENDING' AS case_status,
                       cp.case_key AS search_case_name,
                       CAST(NULL AS VARCHAR(255)) AS representative_label,
                       CAST(NULL AS BIGINT) AS representative_evidence_id,
                       cp.reviewer_id
                FROM case_profiles cp
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM case_evidence ce
                    WHERE ce.owner_id = cp.uploader_id
                      AND ce.case_key = cp.case_key
                )
            ),
            candidates AS (
                SELECT * FROM evidence_candidates
                UNION ALL
                SELECT * FROM profile_candidates
            )
            """.formatted(RESOLVED_CASE_KEY);

    private final EntityManager entityManager;

    public CaseListPage findPage(
            User user,
            String sort,
            int page,
            int size,
            String status,
            String keyword
    ) {
        Scope scope = resolveScope(user);
        String conditions = scopeCondition(scope) + filterConditions(status, keyword);

        Query contentQuery = entityManager.createNativeQuery(
                CASE_CANDIDATES_CTE
                        + " SELECT c.owner_id, c.case_key FROM candidates c WHERE "
                        + conditions
                        + orderBy(sort)
        );
        bindParameters(contentQuery, user, scope, status, keyword);
        contentQuery.setFirstResult(page * size);
        contentQuery.setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = contentQuery.getResultList();
        List<CaseListKey> content = rows.stream()
                .map(row -> new CaseListKey(((Number) row[0]).longValue(), (String) row[1]))
                .toList();

        Query countQuery = entityManager.createNativeQuery(
                CASE_CANDIDATES_CTE
                        + " SELECT COUNT(*) FROM candidates c WHERE "
                        + conditions
        );
        bindParameters(countQuery, user, scope, status, keyword);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        return new CaseListPage(content, totalElements);
    }

    public List<Evidence> findEvidencesByCaseKeys(List<CaseListKey> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT e.*
                FROM evidences e
                WHERE e.status = 'UPLOADED'
                  AND e.deleted_at IS NULL
                  AND (
                """);
        appendCaseKeyPredicates(sql, keys, "e.uploader_id", RESOLVED_CASE_KEY);
        sql.append(") ORDER BY e.evidence_id");

        Query query = entityManager.createNativeQuery(sql.toString(), Evidence.class);
        bindCaseKeys(query, keys);
        @SuppressWarnings("unchecked")
        List<Evidence> evidences = query.getResultList();
        return evidences;
    }

    public List<CaseProfile> findProfilesByCaseKeys(List<CaseListKey> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT cp.* FROM case_profiles cp WHERE ");
        appendCaseKeyPredicates(sql, keys, "cp.uploader_id", "cp.case_key");

        Query query = entityManager.createNativeQuery(sql.toString(), CaseProfile.class);
        bindCaseKeys(query, keys);
        @SuppressWarnings("unchecked")
        List<CaseProfile> profiles = query.getResultList();
        return profiles;
    }

    private void appendCaseKeyPredicates(
            StringBuilder sql,
            List<CaseListKey> keys,
            String ownerExpression,
            String caseKeyExpression
    ) {
        List<String> predicates = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            predicates.add("(" + ownerExpression + " = :owner" + index
                    + " AND " + caseKeyExpression + " = :caseKey" + index + ")");
        }
        sql.append(String.join(" OR ", predicates));
    }

    private void bindCaseKeys(Query query, List<CaseListKey> keys) {
        for (int index = 0; index < keys.size(); index++) {
            query.setParameter("owner" + index, keys.get(index).uploaderId());
            query.setParameter("caseKey" + index, keys.get(index).caseKey());
        }
    }

    private String scopeCondition(Scope scope) {
        return switch (scope) {
            case INVESTIGATOR -> "c.owner_id = :userId";
            case REVIEWER -> "c.reviewer_id = :userId";
            case ORGANIZATION -> """
                    c.owner_id IN (
                        SELECT u.user_id
                        FROM users u
                        WHERE u.deleted_at IS NULL
                          AND u.organization_type = :organizationType
                    )
                    """;
            case GLOBAL -> "1 = 1";
        };
    }

    private String filterConditions(String status, String keyword) {
        StringBuilder conditions = new StringBuilder();
        if (status != null) {
            conditions.append(" AND c.case_status = :status");
        }
        if (keyword != null) {
            conditions.append("""
                     AND (
                         LOWER(COALESCE(c.search_case_name, '')) LIKE :keyword
                         OR LOWER(COALESCE(c.representative_label, '')) LIKE :keyword
                         OR LOWER(CONCAT('evd-', c.representative_evidence_id)) LIKE :keyword
                     )
                    """);
        }
        return conditions.toString();
    }

    private String orderBy(String sort) {
        if ("status".equalsIgnoreCase(sort)) {
            return " ORDER BY c.status_priority ASC, c.created_at DESC, c.owner_id ASC, c.case_key ASC";
        }
        if ("oldest".equalsIgnoreCase(sort)) {
            return " ORDER BY c.created_at ASC, c.owner_id ASC, c.case_key ASC";
        }
        return " ORDER BY c.created_at DESC, c.owner_id ASC, c.case_key ASC";
    }

    private void bindParameters(
            Query query,
            User user,
            Scope scope,
            String status,
            String keyword
    ) {
        if (scope == Scope.INVESTIGATOR || scope == Scope.REVIEWER) {
            query.setParameter("userId", user.getUserId());
        }
        if (scope == Scope.ORGANIZATION) {
            query.setParameter("organizationType", user.getOrganizationType().name());
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        if (keyword != null) {
            query.setParameter("keyword", "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        }
    }

    private Scope resolveScope(User user) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return Scope.GLOBAL;
        }
        if (UserRoleSupport.isOrgAdmin(user.getRole())) {
            return user.getOrganizationType() == null ? Scope.GLOBAL : Scope.ORGANIZATION;
        }
        if (UserRoleSupport.isReviewer(user.getRole())) {
            return Scope.REVIEWER;
        }
        return Scope.INVESTIGATOR;
    }

    private enum Scope {
        INVESTIGATOR,
        REVIEWER,
        ORGANIZATION,
        GLOBAL
    }

    public record CaseListKey(Long uploaderId, String caseKey) {
    }

    public record CaseListPage(List<CaseListKey> content, long totalElements) {
    }
}
