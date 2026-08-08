package com.example.demo.controller;

import com.example.demo.dto.PublicReportAccessIssueResponse;
import com.example.demo.dto.report.ReportListPageResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.report.ReportListService;
import com.example.demo.service.report.ReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "보고서 목록 API")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportListService reportListService;
    private final ReportPdfService reportPdfService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "보고서 목록", description = "사용자가 생성했거나 검토자로 배정된 사건의 발행된 PDF 보고서 목록을 조회합니다.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ReportListPageResponse listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return reportListService.listReports(authUserResolver.requireCurrentUser(), page, size);
    }

    @Operation(summary = "외부 보고서 열람코드 발급", description = "보고서 내용을 외부에 공유하기 위한 RV 열람코드를 발급합니다.")
    @PostMapping(value = "/{reportId}/public-access", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public PublicReportAccessIssueResponse issuePublicAccess(@PathVariable Long reportId) {
        return reportPdfService.issuePublicReportAccess(authUserResolver.requireCurrentUser(), reportId);
    }
}
