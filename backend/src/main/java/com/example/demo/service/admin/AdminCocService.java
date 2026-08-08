package com.example.demo.service.admin;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.admin.AdminCocChainResponse;
import com.example.demo.dto.admin.AdminCocChainsResponse;
import com.example.demo.dto.admin.AdminCocEventResponse;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.custody.CustodyChainVerifier;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCocService {

    private final CustodyLogRepository custodyLogRepository;
    private final EvidenceRepository evidenceRepository;
    private final UserRepository userRepository;
    private final CustodyChainVerifier custodyChainVerifier;

    public AdminCocChainsResponse listEvidenceChains() {
        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeOrderByLogIdAsc(CustodyTargetType.EVIDENCE);
        Map<Long, List<CustodyLog>> logsByEvidence = logs.stream()
                .collect(Collectors.groupingBy(
                        CustodyLog::getTargetId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, Evidence> evidenceById = evidenceRepository
                .findAllById(logsByEvidence.keySet())
                .stream()
                .collect(Collectors.toMap(Evidence::getEvidenceId, Function.identity()));
        Map<Long, User> actorById = userRepository
                .findAllById(logs.stream().map(CustodyLog::getActorId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<AdminCocChainResponse> chains = new ArrayList<>();
        for (Map.Entry<Long, List<CustodyLog>> entry : logsByEvidence.entrySet()) {
            Evidence evidence = evidenceById.get(entry.getKey());
            if (evidence == null) {
                continue;
            }
            chains.add(toChain(evidence, entry.getValue(), actorById));
        }

        chains.sort(Comparator
                .comparing((AdminCocChainResponse chain) -> "BROKEN".equals(chain.getStatus()) ? 0 : 1)
                .thenComparing(AdminCocChainResponse::getLastEventAt, Comparator.reverseOrder()));
        int brokenCount = (int) chains.stream()
                .filter(chain -> "BROKEN".equals(chain.getStatus()))
                .count();

        return AdminCocChainsResponse.builder()
                .totalCount(chains.size())
                .validCount(chains.size() - brokenCount)
                .brokenCount(brokenCount)
                .chains(chains)
                .build();
    }

    private AdminCocChainResponse toChain(
            Evidence evidence,
            List<CustodyLog> logs,
            Map<Long, User> actorById
    ) {
        var verification = custodyChainVerifier.verifyEvidenceChain(evidence.getEvidenceId());
        List<AdminCocEventResponse> events = logs.stream()
                .map(log -> toEvent(log, actorById.get(log.getActorId()), verification.brokenAtLogId()))
                .toList();
        AdminCocEventResponse lastEvent = events.get(events.size() - 1);

        return AdminCocChainResponse.builder()
                .evidenceId(evidence.getEvidenceId())
                .caseId(EvidenceCaseIdResolver.resolve(evidence))
                .caseName(resolveCaseName(evidence))
                .eventCount(events.size())
                .lastEventLabel(lastEvent.getLabel())
                .lastEventAt(lastEvent.getCreatedAt())
                .status(verification.valid() ? "VALID" : "BROKEN")
                .events(events)
                .build();
    }

    private AdminCocEventResponse toEvent(CustodyLog log, User actor, Long brokenAtLogId) {
        return AdminCocEventResponse.builder()
                .logId(log.getLogId())
                .eventType(log.getActionType())
                .label(LogCategoryMapper.resolveActionLabel(log.getActionType()))
                .actor(actor == null ? "사용자 " + log.getActorId() : actor.getName())
                .createdAt(ApiDateTimeFormatter.formatUtc(log.getCreatedAt()))
                .currentLogHash(log.getCurrentLogHash())
                .chainValid(brokenAtLogId == null || !brokenAtLogId.equals(log.getLogId()))
                .detail(log.getReason())
                .build();
    }

    private String resolveCaseName(Evidence evidence) {
        if (evidence.getCaseName() != null && !evidence.getCaseName().isBlank()) {
            return evidence.getCaseName();
        }
        return EvidenceCaseIdResolver.resolve(evidence);
    }
}
