package com.example.demo.service.custody;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.repository.CustodyLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustodyLogService {

    private final CustodyLogRepository custodyLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordUserAction(User actor, User target, String actionType, String reason) {
        record(
                actor.getUserId(),
                CustodyTargetType.USER,
                target.getUserId(),
                actionType,
                null,
                null,
                reason,
                null,
                null
        );
    }

    @Transactional
    public CustodyLog record(
            Long actorId,
            CustodyTargetType targetType,
            Long targetId,
            String actionType,
            String subjectHash,
            String storagePathAtEvent,
            String reason,
            String eventPayloadJson,
            String clientIp
    ) {
        validateRequired(actorId, targetType, targetId, actionType);

        String previousLogHash = custodyLogRepository.findTopByOrderByLogIdDesc()
                .map(CustodyLog::getCurrentLogHash)
                .orElse(null);

        LocalDateTime now = normalizeCreatedAt(LocalDateTime.now());

        CustodyLog log = new CustodyLog();
        log.setActorId(actorId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setActionType(actionType);
        log.setSubjectHash(subjectHash);
        log.setStoragePathAtEvent(storagePathAtEvent);
        log.setReason(reason);
        log.setClientIp(clientIp);
        log.setEventPayloadJson(eventPayloadJson);
        log.setPreviousLogHash(previousLogHash);
        log.setCreatedAt(now);

        log.setCurrentLogHash(sha256Hex(buildHashInput(log)));
        return custodyLogRepository.save(log);
    }

    private void validateRequired(
            Long actorId,
            CustodyTargetType targetType,
            Long targetId,
            String actionType
    ) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType is required");
        }
    }

    private String buildHashInput(CustodyLog log) {
        return String.join("|",
                valueOf(log.getPreviousLogHash()),
                valueOf(log.getActorId()),
                valueOf(log.getTargetType()),
                valueOf(log.getTargetId()),
                valueOf(log.getActionType()),
                valueOf(log.getSubjectHash()),
                valueOf(log.getStoragePathAtEvent()),
                valueOf(log.getReason()),
                // PG json columns may reformat spacing; always hash the canonical Jackson form.
                canonicalJson(log.getEventPayloadJson()),
                valueOf(log.getClientIp()),
                formatCreatedAt(log.getCreatedAt())
        );
    }

    private String canonicalJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(json));
        } catch (JsonProcessingException ex) {
            return json;
        }
    }

    private LocalDateTime normalizeCreatedAt(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.truncatedTo(ChronoUnit.MILLIS);
    }

    private String formatCreatedAt(LocalDateTime value) {
        LocalDateTime normalized = normalizeCreatedAt(value);
        return normalized == null ? "" : normalized.toString();
    }

    private String valueOf(Object value) {
        return value == null ? "" : value.toString();
    }

    @Transactional
    public void recordEvidenceAction(User actor, Evidence evidence, String actionType, String reason) {
        record(
                actor.getUserId(),
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                actionType,
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                reason,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity(CustodyTargetType targetType, Long targetId) {
        return verifyTargetChain(targetType, targetId).valid();
    }

    @Transactional(readOnly = true)
    public TargetChainVerifyResult verifyTargetChain(CustodyTargetType targetType, Long targetId) {
        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId)
                .stream()
                .sorted(Comparator.comparing(CustodyLog::getLogId))
                .toList();

        if (logs.isEmpty()) {
            return TargetChainVerifyResult.valid(0);
        }

        for (CustodyLog log : logs) {
            String expectedPrevious = custodyLogRepository
                    .findTopByLogIdLessThanOrderByLogIdDesc(log.getLogId())
                    .map(CustodyLog::getCurrentLogHash)
                    .orElse(null);

            if (!Objects.equals(expectedPrevious, log.getPreviousLogHash())) {
                return TargetChainVerifyResult.invalid(logs.size(), log.getLogId(), "PREVIOUS_HASH_MISMATCH");
            }

            if (log.getCurrentLogHash() == null || !log.getCurrentLogHash().matches("[0-9a-f]{64}")) {
                return TargetChainVerifyResult.invalid(logs.size(), log.getLogId(), "INVALID_HASH_FORMAT");
            }

            String recomputed = sha256Hex(buildHashInput(log));
            if (!recomputed.equals(log.getCurrentLogHash())) {
                return TargetChainVerifyResult.invalid(logs.size(), log.getLogId(), "HASH_MISMATCH");
            }
        }

        return TargetChainVerifyResult.valid(logs.size());
    }

    public record TargetChainVerifyResult(
            boolean valid,
            int logCount,
            Long brokenAtLogId,
            String failureReason
    ) {
        static TargetChainVerifyResult valid(int logCount) {
            return new TargetChainVerifyResult(true, logCount, null, null);
        }

        static TargetChainVerifyResult invalid(int logCount, Long brokenAtLogId, String failureReason) {
            return new TargetChainVerifyResult(false, logCount, brokenAtLogId, failureReason);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
