package com.example.demo.controller;

import com.example.demo.dto.admin.AdminCocChainsResponse;
import com.example.demo.service.admin.AdminCocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin CoC", description = "관리자 증거 보관 체인 감사 API")
@RestController
@RequestMapping("/api/v1/admin/coc")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCocController {

    private final AdminCocService adminCocService;

    @Operation(summary = "증거별 CoC 체인 목록", description = "증거별 감사 이벤트와 해시 체인 검증 상태를 반환합니다.")
    @GetMapping("/chains")
    public AdminCocChainsResponse listChains() {
        return adminCocService.listEvidenceChains();
    }
}
