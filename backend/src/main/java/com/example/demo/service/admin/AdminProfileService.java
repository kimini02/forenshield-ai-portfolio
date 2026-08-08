package com.example.demo.service.admin;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.dto.admin.AdminProfileResponse;
import com.example.demo.dto.admin.UpdateAdminPasswordRequest;
import com.example.demo.dto.admin.UpdateAdminProfileRequest;
import com.example.demo.exception.AdminException;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminProfileResponse getProfile(User admin) {
        return toResponse(admin);
    }

    @Transactional
    public AdminProfileResponse updateProfile(User admin, UpdateAdminProfileRequest request) {
        User managedAdmin = userRepository.findById(admin.getUserId())
                .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        if (userRepository.existsByLoginIdAndUserIdNotAndDeletedAtIsNull(request.getUsername(), managedAdmin.getUserId())) {
            throw new AdminException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmailAndUserIdNotAndDeletedAtIsNull(request.getEmail(), managedAdmin.getUserId())) {
            throw new AdminException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
        }

        managedAdmin.updateAdminProfile(
                request.getUsername(),
                request.getDisplayName(),
                request.getEmail(),
                request.getDepartment(),
                request.getPhone()
        );

        return toResponse(managedAdmin);
    }

    @Transactional
    public void updatePassword(User admin, UpdateAdminPasswordRequest request) {
        User managedAdmin = userRepository.findById(admin.getUserId())
                .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), managedAdmin.getPassword())) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "현재 비밀번호가 일치하지 않습니다.");
        }

        managedAdmin.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private AdminProfileResponse toResponse(User user) {
        return AdminProfileResponse.builder()
                .username(user.getLoginId())
                .displayName(user.getName())
                .email(user.getEmail())
                .department(user.getDepartment())
                .phone(user.getPhone() == null ? "" : user.getPhone())
                .role(user.getRole() == UserRole.ROLE_ADMIN ? "시스템 관리자" : "일반 사용자")
                .build();
    }
}
