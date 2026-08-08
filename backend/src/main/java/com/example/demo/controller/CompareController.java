package com.example.demo.controller;

import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareOriginalPageResponse;
import com.example.demo.dto.compare.CompareResultResponse;
import com.example.demo.dto.compare.CompareVerifyResponse;
import com.example.demo.dto.compare.RegisteredCompareRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.compare.CompareVerificationService;
import com.example.demo.service.report.ReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Compare", description = "비교 검증 API")
@RestController
@RequestMapping("/api/v1/compare")
@RequiredArgsConstructor
public class CompareController {

    private final CompareVerificationService compareVerificationService;
    private final ReportPdfService reportPdfService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "비교용 원본 증거 목록", description = "RQ-CMP-091: 등록된 원본 증거 검색·선택")
    @GetMapping("/originals")
    public CompareOriginalPageResponse listOriginals(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return compareVerificationService.listOriginals(
                authUserResolver.requireCurrentUser(),
                search,
                page,
                size
        );
    }

    @Operation(summary = "원본 증거 파일 정보", description = "SK-954: 비교 검증 전 원본 파일 기본정보 조회")
    @GetMapping("/originals/{evidenceId}")
    public CompareFileInfoDto getOriginalFileInfo(@PathVariable Long evidenceId) {
        return compareVerificationService.getOriginalFileInfo(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "비교 검증 실행", description = "원본 증거와 업로드한 대상 파일의 해시·메타데이터를 비교합니다.")
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompareVerifyResponse verify(
            @Parameter(description = "원본 증거 ID") @RequestParam("evidenceId") Long evidenceId,
            @Parameter(description = "비교 대상 영상 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "클라이언트 취소 토큰 (선택)") @RequestParam(value = "requestId", required = false) String requestId
    ) {
        return compareVerificationService.verify(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                file,
                requestId
        );
    }

    @Operation(summary = "등록 증거 간 비교 검증", description = "사건에 등록된 두 증거의 해시·메타데이터를 비교합니다.")
    @PostMapping(value = "/verify-registered", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompareVerifyResponse verifyRegistered(
            @Valid @RequestBody RegisteredCompareRequest request
    ) {
        return compareVerificationService.verifyRegistered(
                authUserResolver.requireCurrentUser(),
                request.getOriginalEvidenceId(),
                request.getCandidateEvidenceId()
        );
    }

    @Operation(summary = "비교 검증 취소", description = "클라이언트 측 비교 검증 요청 취소를 수신합니다. 동기 처리이므로 이미 완료된 요청은 무시됩니다.")
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @Parameter(description = "클라이언트 취소 토큰") @RequestParam(value = "requestId", required = false) String requestId
    ) {
        compareVerificationService.cancel(requestId);
    }

    @Operation(summary = "비교 검증 결과 조회")
    @GetMapping("/{compareId}")
    public CompareResultResponse getResult(@PathVariable Long compareId) {
        return compareVerificationService.getResult(
                authUserResolver.requireCurrentUser(),
                compareId
        );
    }

    @Operation(summary = "대조본 파일 정보", description = "SK-955: 비교 검증 대상(대조본) 파일 기본정보 조회")
    @GetMapping("/{compareId}/candidate")
    public CompareFileInfoDto getCandidateFileInfo(@PathVariable Long compareId) {
        return compareVerificationService.getCandidateFileInfo(
                authUserResolver.requireCurrentUser(),
                compareId
        );
    }

    @Operation(summary = "비교 검증 PDF 다운로드", description = "RQ-CMP-104: 비교 검증 결과 PDF 리포트")
    @GetMapping("/{compareId}/reports/pdf")
    public ResponseEntity<byte[]> downloadCompareReport(
            @PathVariable Long compareId,
            @RequestParam(defaultValue = "false") boolean preview
    ) {
        ReportPdfService.ReportPdfPayload payload = reportPdfService.generateCompareReport(
                authUserResolver.requireCurrentUser(),
                compareId,
                preview
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (preview ? "inline" : "attachment") + "; filename=\"" + payload.fileName() + "\"")
                .header("X-Report-Hash", payload.reportHash())
                .header("X-Report-Preview", String.valueOf(preview))
                .header("X-Report-Status", payload.publicationStatus())
                .header("X-Report-Version", String.valueOf(payload.version()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload.content());
    }
}
