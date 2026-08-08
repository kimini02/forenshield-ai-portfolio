package com.example.demo.service.evidence;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceAccessEventType;
import com.example.demo.dto.evidence.EvidenceAccessEventRequest;
import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.util.JsonPayloadWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvidenceAccessAuditService {

    private final EvidenceAccessService evidenceAccessService;
    private final CustodyLogService custodyLogService;
    private final JsonPayloadWriter jsonPayloadWriter;

    @Transactional
    public void recordAccessEvent(
            User user,
            Long evidenceId,
            EvidenceAccessEventRequest request,
            String clientIp
    ) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        EvidenceAccessEventType eventType = request.getEventType();
        String actionType = resolveActionType(eventType);
        String reason = resolveReason(eventType);

        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                actionType,
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                reason,
                jsonPayloadWriter.toJson(accessPayload(request, evidence)),
                clientIp
        );
    }

    private String resolveActionType(EvidenceAccessEventType eventType) {
        return switch (eventType) {
            case VIEW -> "EVIDENCE_VIEWED";
            case CAPTURE_ATTEMPT -> "EVIDENCE_CAPTURE_ATTEMPTED";
        };
    }

    private String resolveReason(EvidenceAccessEventType eventType) {
        return switch (eventType) {
            case VIEW -> "증거 영상 열람";
            case CAPTURE_ATTEMPT -> "증거 화면 캡처 시도 감지";
        };
    }

    private Map<String, Object> accessPayload(EvidenceAccessEventRequest request, Evidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("eventType", request.getEventType().name());
        if (request.getCaseKey() != null && !request.getCaseKey().isBlank()) {
            payload.put("caseKey", request.getCaseKey().trim());
        }
        if (request.getSource() != null && !request.getSource().isBlank()) {
            payload.put("source", request.getSource().trim());
        }
        return payload;
    }
}
