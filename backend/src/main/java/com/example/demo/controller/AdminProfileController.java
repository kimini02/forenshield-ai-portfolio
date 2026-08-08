package com.example.demo.controller;

import com.example.demo.dto.admin.AdminProfileResponse;
import com.example.demo.dto.admin.UpdateAdminPasswordRequest;
import com.example.demo.dto.admin.UpdateAdminProfileRequest;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.admin.AdminProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Profile", description = "관리자 본인 프로필 API")
@RestController
@RequestMapping("/api/v1/admin/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfileController {

    private final AdminProfileService adminProfileService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "관리자 본인 프로필 조회")
    @GetMapping
    public AdminProfileResponse getProfile() {
        return adminProfileService.getProfile(authUserResolver.requireCurrentUser());
    }

    @Operation(summary = "관리자 본인 프로필 수정")
    @PatchMapping
    public AdminProfileResponse updateProfile(@Valid @RequestBody UpdateAdminProfileRequest request) {
        return adminProfileService.updateProfile(authUserResolver.requireCurrentUser(), request);
    }

    @Operation(summary = "관리자 본인 비밀번호 변경")
    @PatchMapping("/password")
    public ResponseEntity<Void> updatePassword(@Valid @RequestBody UpdateAdminPasswordRequest request) {
        adminProfileService.updatePassword(authUserResolver.requireCurrentUser(), request);
        return ResponseEntity.noContent().build();
    }
}
