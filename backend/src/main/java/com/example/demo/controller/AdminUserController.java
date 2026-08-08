package com.example.demo.controller;

import com.example.demo.dto.admin.AdminUserItemResponse;
import com.example.demo.dto.admin.AdminUserPageResponse;
import com.example.demo.dto.admin.AdminUserStatusResponse;
import com.example.demo.dto.admin.ApproveAdminUserRequest;
import com.example.demo.dto.admin.ResetAdminUserPasswordRequest;
import com.example.demo.dto.admin.UpdateAdminUserRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Users", description = "관리자 계정 관리 API")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "계정 목록 조회")
    @GetMapping
    public AdminUserPageResponse listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return adminUserService.listUsers(
                authUserResolver.requireCurrentUser(),
                search,
                status,
                role,
                page,
                size
        );
    }

    @Operation(summary = "가입 승인")
    @PostMapping("/{userId}/approve")
    public AdminUserStatusResponse approve(
            @PathVariable Long userId,
            @RequestBody(required = false) ApproveAdminUserRequest request
    ) {
        return adminUserService.approve(
                authUserResolver.requireCurrentUser(),
                userId,
                request == null ? null : request.getRole()
        );
    }

    @Operation(summary = "가입 반려")
    @PostMapping("/{userId}/reject")
    public AdminUserStatusResponse reject(@PathVariable Long userId) {
        return adminUserService.reject(authUserResolver.requireCurrentUser(), userId);
    }

    @Operation(summary = "계정 정지", description = "RQ-ADMIN-126 · SK-784: APPROVED 계정을 SUSPENDED로 변경")
    @PostMapping("/{userId}/suspend")
    public AdminUserStatusResponse suspend(@PathVariable Long userId) {
        return adminUserService.suspend(authUserResolver.requireCurrentUser(), userId);
    }

    @Operation(summary = "계정 재활성", description = "SUSPENDED 또는 REJECTED 계정을 APPROVED 상태로 복구")
    @PostMapping("/{userId}/reactivate")
    public AdminUserStatusResponse reactivate(@PathVariable Long userId) {
        return adminUserService.reactivate(authUserResolver.requireCurrentUser(), userId);
    }

    @Operation(summary = "계정 정보 수정")
    @PatchMapping("/{userId}")
    public AdminUserItemResponse updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAdminUserRequest request
    ) {
        return adminUserService.updateUser(userId, request);
    }

    @Operation(summary = "계정 비밀번호 재설정")
    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetAdminUserPasswordRequest request
    ) {
        adminUserService.resetPassword(authUserResolver.requireCurrentUser(), userId, request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "계정 삭제")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        adminUserService.deleteUser(authUserResolver.requireCurrentUser(), userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
