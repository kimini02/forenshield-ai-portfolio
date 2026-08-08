package com.example.demo.controller;

import com.example.demo.dto.FileUploadResponse;
import com.example.demo.dto.caseworkflow.ExcludeEvidenceRequest;
import com.example.demo.dto.caseworkflow.SetEvidenceRoleRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.evidence.CaseWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Evidence Workflow", description = "증거 v2 워크플로우 API (제외·대체·역할)")
@RestController
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceWorkflowController {

    private final CaseWorkflowService caseWorkflowService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "증거 사용 제외", description = "원본 증거를 삭제하지 않고 사용 제외(EXCLUDED) 상태로 표시합니다.")
    @PatchMapping("/{evidenceId}/exclude")
    public ResponseEntity<Void> excludeEvidence(
            @PathVariable Long evidenceId,
            @Valid @RequestBody ExcludeEvidenceRequest request
    ) {
        caseWorkflowService.excludeEvidence(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                request.getReason()
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대체 증거 등록", description = "기존 증거를 REPLACED로 표시하고 새 증거 파일을 업로드합니다.")
    @PostMapping(value = "/{evidenceId}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse replaceEvidence(
            @PathVariable Long evidenceId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        return caseWorkflowService.replaceEvidence(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                file,
                reason
        );
    }

    @Operation(summary = "증거 역할 변경", description = "증거 역할(PRIMARY/SUPPLEMENT)을 변경합니다.")
    @PatchMapping("/{evidenceId}/role")
    public ResponseEntity<Void> setEvidenceRole(
            @PathVariable Long evidenceId,
            @Valid @RequestBody SetEvidenceRoleRequest request
    ) {
        caseWorkflowService.setEvidenceRole(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                request.getRole()
        );
        return ResponseEntity.noContent().build();
    }
}
