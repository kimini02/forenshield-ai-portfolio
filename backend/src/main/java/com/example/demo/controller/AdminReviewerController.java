package com.example.demo.controller;

import com.example.demo.dto.admin.AdminReviewerListResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Reviewers", description = "검토자 배정용 검토자 목록 API")
@RestController
@RequestMapping("/api/v1/admin/reviewers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewerController {

    private final AdminUserService adminUserService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "검토자 목록", description = "승인된 검토자 계정 목록. uploaderId를 전달하면 해당 수사관과 같은 기관·부서만 조회합니다.")
    @GetMapping
    public AdminReviewerListResponse listReviewers(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String uploaderId
    ) {
        return adminUserService.listReviewers(
                authUserResolver.requireCurrentUser(),
                department,
                uploaderId
        );
    }
}
