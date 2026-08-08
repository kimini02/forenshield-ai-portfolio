package com.example.demo.controller;

import com.example.demo.dto.admin.AdminEvidenceDetailResponse;
import com.example.demo.dto.admin.AdminEvidencePageResponse;
import com.example.demo.dto.admin.DeleteAdminEvidenceRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.admin.AdminEvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Evidences", description = "관리자 증거 관리 API")
@RestController
@RequestMapping("/api/v1/admin/evidences")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminEvidenceController {

    private final AdminEvidenceService adminEvidenceService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "증거 목록 조회")
    @GetMapping
    public AdminEvidencePageResponse listEvidences(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return adminEvidenceService.listEvidences(search, fileType, status, department, from, to, page, size);
    }

    @Operation(summary = "증거 상세 조회")
    @GetMapping("/{evidenceId}")
    public AdminEvidenceDetailResponse getEvidence(@PathVariable Long evidenceId) {
        return adminEvidenceService.getEvidence(evidenceId);
    }

    @Operation(summary = "증거 삭제 (soft delete)")
    @DeleteMapping("/{evidenceId}")
    public ResponseEntity<Void> deleteEvidence(
            @PathVariable Long evidenceId,
            @Valid @RequestBody DeleteAdminEvidenceRequest request
    ) {
        adminEvidenceService.deleteEvidence(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                request.getReason()
        );
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
