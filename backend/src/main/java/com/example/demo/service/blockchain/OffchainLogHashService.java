package com.example.demo.service.blockchain;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Report;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.service.evidence.HashService;
import com.example.demo.util.ApiDateTimeFormatter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Canonical SHA-256 over off-chain custody / report detail bundles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OffchainLogHashService {

    private final CustodyLogRepository custodyLogRepository;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    public String hashEvidenceCustodyBundle(Long evidenceId) {
        if (evidenceId == null) {
            return null;
        }
        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);
        return hashCustodyLogs(logs);
    }

    public String hashDailyCustodyBundle(LocalDate batchDate) {
        if (batchDate == null) {
            return null;
        }
        LocalDateTime start = batchDate.atStartOfDay();
        LocalDateTime end = batchDate.plusDays(1).atStartOfDay();
        List<CustodyLog> logs = custodyLogRepository.findAll().stream()
                .filter(logEntry -> logEntry.getCreatedAt() != null)
                .filter(logEntry -> !logEntry.getCreatedAt().isBefore(start)
                        && logEntry.getCreatedAt().isBefore(end))
                .sorted(Comparator
                        .comparing(CustodyLog::getCreatedAt)
                        .thenComparing(CustodyLog::getLogId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        return hashCustodyLogs(logs);
    }

    public String hashReportBundle(Report report) {
        if (report == null) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportId", report.getReportId());
        body.put("evidenceId", report.getEvidenceId());
        body.put("reportHash", report.getReportHash());
        body.put("storagePath", report.getStoragePath());
        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.REPORT, report.getReportId());
        body.put("custodyLogs", toCanonicalLogEntries(logs));
        return hashCanonical(body);
    }

    private String hashCustodyLogs(List<CustodyLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return null;
        }
        return hashCanonical(toCanonicalLogEntries(logs));
    }

    private List<Map<String, Object>> toCanonicalLogEntries(List<CustodyLog> logs) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (CustodyLog logEntry : logs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("logId", logEntry.getLogId());
            entry.put("actionType", logEntry.getActionType());
            entry.put("subjectHash", logEntry.getSubjectHash());
            entry.put("currentLogHash", logEntry.getCurrentLogHash());
            entry.put("eventPayloadJson", parseJsonOrRaw(logEntry.getEventPayloadJson()));
            entry.put("createdAt", ApiDateTimeFormatter.formatUtc(logEntry.getCreatedAt()));
            entries.add(entry);
        }
        return entries;
    }

    private Object parseJsonOrRaw(String eventPayloadJson) {
        if (eventPayloadJson == null || eventPayloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(eventPayloadJson);
        } catch (JsonProcessingException ex) {
            return eventPayloadJson;
        }
    }

    private String hashCanonical(Object value) {
        try {
            ObjectMapper canonical = objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            String json = canonical.writeValueAsString(value);
            return hashService.generateSha256(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to canonicalize off-chain log bundle", ex);
            return null;
        }
    }
}
