package com.example.demo.controller;

import com.example.demo.dto.user.UpdateUserProfileRequest;
import com.example.demo.dto.user.UpdateUserSettingsRequest;
import com.example.demo.dto.user.UserProfileResponse;
import com.example.demo.dto.user.UserSettingsResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.user.UserService;
import com.example.demo.service.user.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 프로필 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final UserSettingsService userSettingsService;
	private final AuthUserResolver authUserResolver;

	@Operation(summary = "내 프로필 조회")
	@GetMapping("/me")
	public UserProfileResponse getMyProfile() {
		return userService.getProfile(authUserResolver.requireCurrentUser());
	}

	@Operation(summary = "내 프로필 수정")
	@PatchMapping("/me")
	public UserProfileResponse updateMyProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
		return userService.updateProfile(authUserResolver.requireCurrentUser(), request);
	}

	@Operation(summary = "환경 설정 조회", description = "RQ-COM-009, RQ-MY-112")
	@GetMapping("/me/settings")
	public UserSettingsResponse getMySettings() {
		return userSettingsService.getSettings(authUserResolver.requireCurrentUser().getUserId());
	}

	@Operation(summary = "환경 설정 수정", description = "RQ-COM-009, RQ-MY-112")
	@PatchMapping("/me/settings")
	public UserSettingsResponse updateMySettings(@RequestBody UpdateUserSettingsRequest request) {
		return userSettingsService.updateSettings(authUserResolver.requireCurrentUser().getUserId(), request);
	}
}
