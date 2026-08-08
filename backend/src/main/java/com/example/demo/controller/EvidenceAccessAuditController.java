package com.example.demo.controller;

import com.example.demo.dto.evidence.EvidenceAccessEventRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.evidence.EvidenceAccessAuditService;
import com.example.demo.util.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evidence Access Audit", description = "증거 열람·캡처 시도 감사 로그 API")
@RestController
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceAccessAuditController {

    private final EvidenceAccessAuditService evidenceAccessAuditService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "증거 접근 이벤트 기록",
            description = "증거 영상 열람(VIEW) 또는 화면 캡처 시도(CAPTURE_ATTEMPT)를 CoC 감사 로그에 기록합니다."
    )
    @PostMapping("/{evidenceId}/access-events")
    public ResponseEntity<Void> recordAccessEvent(
            @PathVariable Long evidenceId,
            @Valid @RequestBody EvidenceAccessEventRequest request,
            HttpServletRequest httpRequest
    ) {
        evidenceAccessAuditService.recordAccessEvent(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                request,
                ClientIpResolver.resolve(httpRequest)
        );
        return ResponseEntity.noContent().build();
    }
}
