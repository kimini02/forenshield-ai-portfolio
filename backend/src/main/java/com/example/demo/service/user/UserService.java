package com.example.demo.service.user;

import com.example.demo.domain.User;
import com.example.demo.domain.UserSetting;
import com.example.demo.domain.enums.ThemeMode;
import com.example.demo.dto.user.UpdateUserProfileRequest;
import com.example.demo.dto.user.UserProfileResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.util.UserRoleSupport;
import java.time.format.DateTimeFormatter;

import com.example.demo.util.OrganizationIdResolver;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserSettingsService userSettingsService;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(User user) {
		return toResponse(user);
	}

	public UserProfileResponse updateProfile(User user, UpdateUserProfileRequest request) {
		User managedUser = userRepository.findById(user.getUserId())
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다."));

		if (!passwordEncoder.matches(request.getCurrentPassword(), managedUser.getPassword())) {
			throw new BusinessException(
					HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "현재 비밀번호가 일치하지 않습니다.");
		}

		if (userRepository.existsByLoginIdAndUserIdNotAndDeletedAtIsNull(request.getLoginId(), managedUser.getUserId())) {
			throw new BusinessException(
					HttpStatus.BAD_REQUEST, "DUPLICATE_LOGIN_ID", "이미 사용 중인 사용자 이름입니다.");
		}

		managedUser.updateProfile(request.getLoginId());

		if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
			if (request.getNewPassword().length() < 8) {
				throw new BusinessException(
						HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT", "비밀번호는 최소 8자 이상이어야 합니다.");
			}
			managedUser.updatePassword(passwordEncoder.encode(request.getNewPassword()));
		}

		return toResponse(managedUser);
	}

	private UserProfileResponse toResponse(User user) {
		ThemeMode themeMode = userSettingsService.getThemeMode(user.getUserId());
		return UserProfileResponse.builder()
				.userId(user.getUserId())
				.loginId(user.getLoginId())
				.email(user.getEmail())
				.name(user.getName())
				.department(user.getDepartment())
				.role(UserRoleSupport.toApiRole(user.getRole()).name())
				.status(user.getStatus().name())
				.darkMode(themeMode == ThemeMode.DARK || Boolean.TRUE.equals(user.getDarkMode()))
				.themeMode(themeMode)
				.createdAt(ISO_FORMATTER.format(user.getCreatedAt()))
                .organizationType(user.getOrganizationType().name())
                .organizationId(OrganizationIdResolver.resolve(user.getOrganizationType()))
                .organizationName(OrganizationIdResolver.displayName(user.getOrganizationType()))
                .build();
	}
}
