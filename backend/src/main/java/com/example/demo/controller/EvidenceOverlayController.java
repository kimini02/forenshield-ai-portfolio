package com.example.demo.controller;

import com.example.demo.config.OpenApiConfig;
import com.example.demo.dto.OverlayJobStatusResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.overlay.OverlayJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Evidence Overlay", description = "온디맨드 모델 오버레이 생성")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceOverlayController {

    private final OverlayJobService overlayJobService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "모듈 오버레이 생성 요청")
    @PostMapping("/{evidenceId}/overlays/{module}/generate")
    public OverlayJobStatusResponse generate(
            @PathVariable Long evidenceId,
            @PathVariable String module
    ) {
        try {
            return overlayJobService.generate(authUserResolver.requireCurrentUser(), evidenceId, module);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @Operation(summary = "오버레이 생성 작업 상태 조회")
    @GetMapping("/{evidenceId}/overlays/jobs/{overlayJobId}")
    public OverlayJobStatusResponse status(
            @PathVariable Long evidenceId,
            @PathVariable Long overlayJobId
    ) {
        try {
            return overlayJobService.getStatus(authUserResolver.requireCurrentUser(), evidenceId, overlayJobId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
