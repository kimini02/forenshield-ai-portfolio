package com.example.demo.controller;

import com.example.demo.dto.admin.AdminInviteCodeResponse;
import com.example.demo.dto.admin.CreateInviteCodeRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.admin.AdminInviteCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Invite Codes", description = "관리자 초대코드 API")
@RestController
@RequestMapping("/api/v1/admin/invite-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInviteCodeController {

    private final AdminInviteCodeService adminInviteCodeService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "초대코드 목록 조회")
    @GetMapping
    public List<AdminInviteCodeResponse> listInviteCodes() {
        return adminInviteCodeService.listInviteCodes();
    }

    @Operation(summary = "초대코드 발급")
    @PostMapping
    public ResponseEntity<AdminInviteCodeResponse> createInviteCode(
            @RequestBody(required = false) CreateInviteCodeRequest request
    ) {
        CreateInviteCodeRequest body = request == null ? new CreateInviteCodeRequest() : request;
        AdminInviteCodeResponse response = adminInviteCodeService.createInviteCode(
                authUserResolver.requireCurrentUser(),
                body
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
