package com.example.demo.controller;

import com.example.demo.dto.caseworkflow.AssignCaseReviewerRequest;
import com.example.demo.dto.caseworkflow.CreateCaseRequest;
import com.example.demo.dto.caseworkflow.SetRepresentativeEvidenceRequest;
import com.example.demo.dto.caseworkflow.UpdateCaseNameRequest;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.CaseReviewDecisionRequest;
import com.example.demo.dto.detail.CaseReviewRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.evidence.CaseReviewService;
import com.example.demo.service.evidence.CaseWorkflowService;
import com.example.demo.service.evidence.EvidenceDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Case", description = "사건 상세 API")
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final EvidenceDetailService evidenceDetailService;
    private final CaseWorkflowService caseWorkflowService;
    private final CaseReviewService caseReviewService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "사건 상세", description = "사건 ID(사건명)로 소속 증거 목록을 조회합니다.")
    @GetMapping(value = {"", "/{caseId}"}, produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse getCaseDetail(
            @PathVariable(required = false) String caseId,
            @RequestParam(required = false) String caseKey,
            @RequestParam(required = false) Long uploaderId
    ) {
        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                caseKey,
                caseId,
                uploaderId
        );
    }

    @Operation(summary = "사건 등록", description = "증거 없이 사건명만으로 case_profiles를 생성합니다. v2 사건 생성 UI 연동.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse createCase(@Valid @RequestBody CreateCaseRequest request) {
        String caseKey = caseWorkflowService.createCase(
                authUserResolver.requireCurrentUser(),
                request.getCaseName()
        );
        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                caseKey
        );
    }

    @Operation(summary = "대표 증거 지정", description = "사건의 대표 증거를 지정합니다. v2 사건 관리 UI 연동.")
    @PatchMapping(value = "/representative", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<Void> setRepresentativeEvidence(
            @RequestParam String caseKey,
            @Valid @RequestBody SetRepresentativeEvidenceRequest request
    ) {
        caseWorkflowService.setRepresentativeEvidence(
                authUserResolver.requireCurrentUser(),
                caseKey,
                request.getEvidenceId()
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "사건명 변경", description = "사건에 속한 모든 증거의 사건명을 일괄 변경합니다. v2 사건 편집 UI 연동.")
    @PatchMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse updateCaseName(
            @RequestParam String caseKey,
            @Valid @RequestBody UpdateCaseNameRequest request
    ) {
        String newCaseKey = caseWorkflowService.renameCase(
                authUserResolver.requireCurrentUser(),
                caseKey,
                request.getCaseName()
        );
        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                newCaseKey
        );
    }

    @Operation(summary = "검토 요청", description = "분석이 완료된 사건에 대해 검토를 요청합니다.")
    @PostMapping(value = "/review-request", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse requestReview(
            @RequestParam String caseKey,
            @RequestBody(required = false) CaseReviewRequest request
    ) {
        String memo = request == null ? null : request.getMemo();
        return caseReviewService.requestReview(
                authUserResolver.requireCurrentUser(),
                caseKey,
                memo
        );
    }

    @Operation(summary = "검토자 배정 (v2)", description = "기관 관리자가 검토 요청 사건에 검토자를 배정합니다.")
    @PatchMapping(value = "/reviewer", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse assignReviewerByCaseKey(
            @RequestParam String caseKey,
            @Valid @RequestBody com.example.demo.dto.detail.AssignCaseReviewerRequest request
    ) {
        return caseReviewService.assignReviewer(
                authUserResolver.requireCurrentUser(),
                caseKey,
                request.getReviewerId()
        );
    }

    @Operation(summary = "검토자 배정", description = "기관 관리자가 사건에 검토자를 배정합니다.")
    @PatchMapping(value = "/{caseId}/reviewer", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse assignReviewer(
            @PathVariable String caseId,
            @Valid @RequestBody AssignCaseReviewerRequest request
    ) {
        Long ownerId = caseWorkflowService.assignReviewer(
                authUserResolver.requireCurrentUser(),
                caseId,
                request
        );
        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                caseId,
                ownerId
        );
    }

    @Operation(summary = "검토 결정", description = "배정된 검토자가 승인 또는 재검토를 기록합니다.")
    @PostMapping(value = "/review-decision", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse recordReviewDecision(
            @RequestParam String caseKey,
            @Valid @RequestBody CaseReviewDecisionRequest request
    ) {
        return caseReviewService.recordDecision(
                authUserResolver.requireCurrentUser(),
                caseKey,
                request.getDecision(),
                request.getMemo()
        );
    }
}
