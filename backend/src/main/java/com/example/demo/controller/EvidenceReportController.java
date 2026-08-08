package com.example.demo.controller;

import com.example.demo.dto.ReportVerifyResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.report.ReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evidence Report", description = "증거 분석 PDF 리포트 API")
@RestController
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceReportController {

    private final ReportPdfService reportPdfService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "분석 PDF 리포트 다운로드", description = "RQ-DTL-082~086: 분석 결과 PDF 리포트")
    @GetMapping("/{evidenceId}/reports/pdf")
    public ResponseEntity<byte[]> downloadAnalysisReport(
            @PathVariable Long evidenceId,
            @RequestParam(defaultValue = "false") boolean preview
    ) {
        ReportPdfService.ReportPdfPayload payload = reportPdfService.generateEvidenceReport(
                authUserResolver.requireCurrentUser(),
                evidenceId,
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

    @Operation(summary = "PDF reportHash 검증", description = "RQ-DTL-087: 저장된 PDF reportHash 무결성 검증")
    @GetMapping("/{evidenceId}/reports/verify")
    public ReportVerifyResponse verifyAnalysisReport(
            @PathVariable Long evidenceId,
            @RequestParam("reportHash") String reportHash
    ) {
        return reportPdfService.verifyReportHash(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                reportHash
        );
    }
}
