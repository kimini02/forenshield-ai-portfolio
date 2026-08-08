package com.example.demo.controller;

import com.example.demo.dto.admin.AdminLogPageResponse;
import com.example.demo.exception.AdminException;
import com.example.demo.service.admin.AdminLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "Admin Logs", description = "관리자 로그 API")
@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

    private final AdminLogService adminLogService;

    @Operation(summary = "로그 목록 조회")
    @GetMapping
    public AdminLogPageResponse listLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        return adminLogService.listLogs(category, department, search, from, to, page, size);
    }

    @Operation(summary = "로그 CSV 내보내기")
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (!"csv".equalsIgnoreCase(format)) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_FORMAT", "지원하지 않는 내보내기 형식입니다.");
        }

        byte[] csv = adminLogService.exportLogsAsCsv(category, department, search, from, to);
        String filename = "admin-logs-" + LocalDate.now() + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv);
    }
}
