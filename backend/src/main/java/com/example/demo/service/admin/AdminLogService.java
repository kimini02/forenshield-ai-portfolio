package com.example.demo.service.admin;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.User;
import com.example.demo.dto.admin.AdminLogItemResponse;
import com.example.demo.dto.admin.AdminLogPageResponse;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final CustodyLogRepository custodyLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminLogPageResponse listLogs(
            String category,
            String department,
            String search,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        Specification<CustodyLog> specification = buildSpecification(category, department, search, from, to);
        Page<CustodyLog> result = custodyLogRepository.findAll(specification, PageRequest.of(page, size));

        Map<Long, User> actorsById = resolveActors(result.getContent());

        return AdminLogPageResponse.builder()
                .content(result.getContent().stream()
                        .map(log -> toItem(log, actorsById))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .departments(userRepository.findDistinctDepartments())
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportLogsAsCsv(
            String category,
            String department,
            String search,
            LocalDate from,
            LocalDate to
    ) {
        Specification<CustodyLog> specification = buildSpecification(category, department, search, from, to);
        List<CustodyLog> logs = custodyLogRepository.findAll(
                specification,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Map<Long, User> actorsById = resolveActors(logs);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("timestamp,category,department,actor,actorName,action,detail").append('\n');

        for (CustodyLog log : logs) {
            AdminLogItemResponse item = toItem(log, actorsById);
            csv.append(escapeCsv(item.getTimestamp())).append(',');
            csv.append(escapeCsv(item.getCategory())).append(',');
            csv.append(escapeCsv(item.getDepartment())).append(',');
            csv.append(escapeCsv(item.getActor())).append(',');
            csv.append(escapeCsv(item.getActorName())).append(',');
            csv.append(escapeCsv(item.getAction())).append(',');
            csv.append(escapeCsv(item.getDetail())).append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Specification<CustodyLog> buildSpecification(
            String category,
            String department,
            String search,
            LocalDate from,
            LocalDate to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            List<String> actionTypes = LogCategoryMapper.actionTypesForCategory(category);
            if (!actionTypes.isEmpty()) {
                predicates.add(root.get("actionType").in(actionTypes));
            }

            if (department != null && !department.isBlank() && !"ALL".equalsIgnoreCase(department)) {
                List<Long> actorIds = userRepository.findUserIdsByDepartment(department.trim());
                if (actorIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("actorId").in(actorIds));
                }
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }

            String normalizedSearch = search == null ? "" : search.trim();
            if (!normalizedSearch.isEmpty()) {
                String pattern = "%" + normalizedSearch.toLowerCase() + "%";
                List<Long> matchingActorIds = userRepository.findUserIdsByLoginIdOrName(normalizedSearch);
                Predicate actorMatched = matchingActorIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("actorId").in(matchingActorIds);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actionType")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("reason"), "")), pattern),
                        actorMatched
                ));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<Long, User> resolveActors(List<CustodyLog> logs) {
        List<Long> actorIds = logs.stream()
                .map(CustodyLog::getActorId)
                .distinct()
                .toList();

        if (actorIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user));
    }

    private AdminLogItemResponse toItem(CustodyLog log, Map<Long, User> actorsById) {
        User actor = actorsById.get(log.getActorId());
        String actorLoginId = actor == null ? "unknown" : actor.getLoginId();
        String actorName = actor == null ? null : actor.getName();
        String department = actor == null ? "-" : actor.getDepartment();

        return AdminLogItemResponse.builder()
                .id(String.valueOf(log.getLogId()))
                .timestamp(formatLogTimestamp(log.getCreatedAt()))
                .category(LogCategoryMapper.resolveCategory(log.getActionType()))
                .actor(actorLoginId)
                .actorId(String.valueOf(log.getActorId()))
                .actorName(actorName)
                .department(department)
                .action(LogCategoryMapper.resolveActionLabel(log.getActionType()))
                .detail(log.getReason())
                .build();
    }

    private String formatLogTimestamp(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return value.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(SEOUL_ZONE)
                .format(LOG_TIMESTAMP_FORMATTER);
    }
}
