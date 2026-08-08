package com.example.demo.domain;

import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, unique = true, length = 100)
    private String loginId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 30)
    private OrgType organizationType;

    @Column(name = "department", nullable = false, length = 255)
    private String department;

    @Column(name = "position", length = 255)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "dark_mode", nullable = false)
    private Boolean darkMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_code_id")
    private InviteCode inviteCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(
            String loginId,
            String email,
            String password,
            String name,
            String phone,
            OrgType organizationType,
            String department,
            String position,
            InviteCode inviteCode,
            UserRole role,
            UserStatus status,
            Boolean darkMode
    ) {
        this.loginId = loginId;
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.organizationType = organizationType;
        this.department = department;
        this.position = position;
        this.role = role != null ? role : UserRole.ROLE_USER;
        this.status = status != null ? status : UserStatus.PENDING;
        this.darkMode = darkMode != null ? darkMode : false;
        this.inviteCode = inviteCode;
    }

    public void updateProfile(String loginId) {
        this.loginId = loginId;
    }

    public void updateAccountInfo(String name, String email, String department) {
        this.name = name;
        this.email = email;
        this.department = department;
    }

    public void updateAccountInfo(String name, String email, OrgType organizationType, String department) {
        this.name = name;
        this.email = email;
        if (organizationType != null) {
            this.organizationType = organizationType;
        }
        this.department = department;
    }

    public void updateAdminProfile(String loginId, String name, String email, String department, String phone) {
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.department = department;
        this.phone = phone;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateStatus(UserStatus status) {
        this.status = status;
    }

    public void updateRole(UserRole role) {
        this.role = role;
    }

    public void updateDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    /** 로컬 개발용 계정(1111/3333) 로그인 보장 */
    public void syncDevCredentials(UserRole role, String encodedPassword) {
        this.role = role;
        this.status = UserStatus.APPROVED;
        this.password = encodedPassword;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
