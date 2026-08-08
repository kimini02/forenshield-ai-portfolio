package com.example.demo.service.admin;

import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.admin.AdminReviewerItemResponse;
import com.example.demo.dto.admin.AdminReviewerListResponse;
import com.example.demo.dto.admin.AdminUserItemResponse;
import com.example.demo.dto.admin.AdminUserPageResponse;
import com.example.demo.dto.admin.AdminUserStatusResponse;
import com.example.demo.dto.admin.UpdateAdminUserRequest;
import com.example.demo.exception.AdminException;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.OrganizationIdResolver;
import com.example.demo.util.UserRoleSupport;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final CustodyLogService custodyLogService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(User admin, String search, String status, String role, int page, int size) {
        UserStatus statusFilter = parseStatusFilter(status);
        UserRole roleFilter = parseRoleFilter(role);
        String normalizedSearch = search == null ? "" : search.trim();
        OrgType organizationFilter = resolveOrganizationFilter(admin);

        Page<User> result = userRepository.findAdminUsers(
                statusFilter,
                roleFilter,
                organizationFilter,
                normalizedSearch,
                PageRequest.of(page, size)
        );

        return AdminUserPageResponse.builder()
                .content(result.getContent().stream().map(this::toItem).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminReviewerListResponse listReviewers(User admin, String department, String uploaderIdValue) {
        OrgType organizationFilter = resolveOrganizationFilter(admin);
        String departmentFilter = normalizeOptionalFilter(department);

        if (uploaderIdValue != null && !uploaderIdValue.isBlank()) {
            User uploader = findReviewerScopeUploader(uploaderIdValue);
            if (organizationFilter != null && organizationFilter != uploader.getOrganizationType()) {
                throw new AdminException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "같은 기관 사건의 검토자만 조회할 수 있습니다.");
            }

            organizationFilter = uploader.getOrganizationType();
            departmentFilter = normalizeOptionalFilter(uploader.getDepartment());
            if (organizationFilter == null || departmentFilter == null) {
                return AdminReviewerListResponse.builder()
                        .reviewers(List.of())
                        .build();
            }
        }

        List<AdminReviewerItemResponse> reviewers = userRepository
                .findApprovedReviewers(organizationFilter, departmentFilter)
                .stream()
                .map(this::toReviewerItem)
                .toList();

        return AdminReviewerListResponse.builder()
                .reviewers(reviewers)
                .build();
    }

    @Transactional
    public AdminUserStatusResponse approve(User admin, Long userId, String role) {
        User target = findActiveUser(userId);
        ensurePending(target, "승인");
        target.updateStatus(UserStatus.APPROVED);
        if (role != null && !role.isBlank()) {
            target.updateRole(parseAssignableRole(role));
        }
        custodyLogService.recordUserAction(admin, target, "USER_APPROVED", target.getLoginId());
        return toStatusResponse(target, admin.getUserId());
    }

    @Transactional
    public AdminUserStatusResponse reject(User admin, Long userId) {
        User target = findActiveUser(userId);
        ensurePending(target, "반려");
        target.updateStatus(UserStatus.REJECTED);
        custodyLogService.recordUserAction(admin, target, "USER_REJECTED", target.getLoginId());
        return toStatusResponse(target, admin.getUserId());
    }

    @Transactional
    public AdminUserStatusResponse suspend(User admin, Long userId) {
        if (admin.getUserId().equals(userId)) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "CANNOT_SUSPEND_SELF", "본인 계정은 정지할 수 없습니다.");
        }

        User target = findActiveUser(userId);
        if (target.getStatus() == UserStatus.SUSPENDED) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "ALREADY_SUSPENDED", "이미 정지된 계정입니다.");
        }
        if (target.getStatus() != UserStatus.APPROVED) {
            throw new AdminException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USER_STATUS",
                    "APPROVED 상태의 계정만 정지할 수 있습니다."
            );
        }

        target.updateStatus(UserStatus.SUSPENDED);
        custodyLogService.recordUserAction(admin, target, "USER_SUSPENDED", target.getLoginId());
        return toStatusResponse(target, admin.getUserId());
    }

    @Transactional
    public AdminUserStatusResponse reactivate(User admin, Long userId) {
        User target = findActiveUser(userId);
        if (target.getStatus() != UserStatus.SUSPENDED && target.getStatus() != UserStatus.REJECTED) {
            throw new AdminException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USER_STATUS",
                    "SUSPENDED 또는 REJECTED 상태의 계정만 재활성할 수 있습니다."
            );
        }

        target.updateStatus(UserStatus.APPROVED);
        custodyLogService.recordUserAction(admin, target, "USER_REACTIVATED", target.getLoginId());
        return toStatusResponse(target, admin.getUserId());
    }

    @Transactional
    public AdminUserItemResponse updateUser(Long userId, UpdateAdminUserRequest request) {
        User target = findActiveUser(userId);

        if (userRepository.existsByEmailAndUserIdNotAndDeletedAtIsNull(request.getEmail(), target.getUserId())) {
            throw new AdminException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
        }

        target.updateAccountInfo(
                request.getDisplayName(),
                request.getEmail(),
                request.getOrganizationType(),
                request.getDepartment()
        );
        if (request.getRole() != null && !request.getRole().isBlank()) {
            target.updateRole(parseAssignableRole(request.getRole()));
        }
        return toItem(target);
    }

    @Transactional
    public void resetPassword(User admin, Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT", "비밀번호는 8자 이상이어야 합니다.");
        }

        User target = findActiveUser(userId);
        target.updatePassword(passwordEncoder.encode(newPassword));
        custodyLogService.recordUserAction(admin, target, "USER_PASSWORD_RESET", target.getLoginId());
    }

    @Transactional
    public void deleteUser(User admin, Long userId) {
        User target = findActiveUser(userId);
        target.softDelete();
        custodyLogService.recordUserAction(admin, target, "USER_DELETED", target.getLoginId());
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private void ensurePending(User target, String actionLabel) {
        if (target.getStatus() != UserStatus.PENDING) {
            throw new AdminException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USER_STATUS",
                    "PENDING 상태의 계정만 " + actionLabel + "할 수 있습니다."
            );
        }
    }

    private UserStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "유효하지 않은 상태 값입니다.");
        }
    }

    private UserRole parseRoleFilter(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return parseAssignableRole(role);
        } catch (IllegalArgumentException ex) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "유효하지 않은 역할 값입니다.");
        }
    }

    private UserRole parseAssignableRole(String role) {
        try {
            return UserRoleSupport.parseAssignableRole(role);
        } catch (IllegalArgumentException ex) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "유효하지 않은 역할 값입니다.");
        }
    }

    private AdminUserItemResponse toItem(User user) {
        return AdminUserItemResponse.builder()
                .id(String.valueOf(user.getUserId()))
                .username(user.getLoginId())
                .displayName(user.getName())
                .email(user.getEmail())
                .department(user.getDepartment())
                .joinedAt(user.getCreatedAt().toLocalDate().toString())
                .status(user.getStatus().name())
                .role(UserRoleSupport.toClientRole(user.getRole()))
                .organizationType(user.getOrganizationType() == null ? null : user.getOrganizationType().name())
                .organizationId(OrganizationIdResolver.resolve(user.getOrganizationType()))
                .organizationName(OrganizationIdResolver.displayName(user.getOrganizationType()))
                .build();
    }

    private AdminReviewerItemResponse toReviewerItem(User user) {
        return AdminReviewerItemResponse.builder()
                .id(String.valueOf(user.getUserId()))
                .name(user.getName())
                .department(user.getDepartment())
                .organizationType(user.getOrganizationType() == null ? null : user.getOrganizationType().name())
                .organizationId(OrganizationIdResolver.resolve(user.getOrganizationType()))
                .organizationName(OrganizationIdResolver.displayName(user.getOrganizationType()))
                .build();
    }

    private OrgType resolveOrganizationFilter(User admin) {
        if (admin.getRole() == UserRole.ROLE_ORG_ADMIN) {
            return admin.getOrganizationType();
        }
        return null;
    }

    private String normalizeOptionalFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private User findReviewerScopeUploader(String uploaderIdValue) {
        Long uploaderId;
        try {
            uploaderId = Long.parseLong(uploaderIdValue.trim());
        } catch (NumberFormatException ex) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_UPLOADER", "uploaderId 형식이 올바르지 않습니다.");
        }

        return userRepository.findByUserIdAndDeletedAtIsNull(uploaderId)
                .orElseThrow(() -> new AdminException(
                        HttpStatus.NOT_FOUND,
                        "UPLOADER_NOT_FOUND",
                        "사건 담당 분석관을 찾을 수 없습니다."
                ));
    }

    private AdminUserStatusResponse toStatusResponse(User user, Long processedByUserId) {
        return AdminUserStatusResponse.builder()
                .userId(String.valueOf(user.getUserId()))
                .status(user.getStatus().name())
                .processedByUserId(processedByUserId == null ? null : String.valueOf(processedByUserId))
                .build();
    }
}
