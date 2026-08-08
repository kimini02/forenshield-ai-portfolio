package com.example.demo.domain;

import com.example.demo.domain.enums.InviteStatus;
import com.example.demo.domain.enums.OrgType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "invite_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invite_code_id")
    private Long inviteCodeId;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 30)
    private OrgType organizationType;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InviteStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_by")
    private Long usedBy;

    @Builder
    public InviteCode(
            String code,
            OrgType organizationType,
            Long issuedBy,
            InviteStatus status,
            LocalDateTime expiresAt
    ) {
        this.code = code;
        this.organizationType = organizationType;
        this.issuedBy = issuedBy;
        this.status = status == null ? InviteStatus.ACTIVE : status;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isUsable(LocalDateTime now) {
        return status == InviteStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void markUsedBy(Long userId, LocalDateTime usedAt) {
        this.status = InviteStatus.USED;
        this.usedBy = userId;
        this.usedAt = usedAt;
    }
}
