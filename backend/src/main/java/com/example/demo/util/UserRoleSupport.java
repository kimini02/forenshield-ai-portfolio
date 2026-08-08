package com.example.demo.util;

import com.example.demo.domain.enums.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class UserRoleSupport {

    private UserRoleSupport() {
    }

    public static UserRole parseAssignableRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INVESTIGATOR", "ROLE_INVESTIGATOR", "ROLE_USER", "USER" -> UserRole.ROLE_INVESTIGATOR;
            case "REVIEWER", "ROLE_REVIEWER" -> UserRole.ROLE_REVIEWER;
            case "ORG_ADMIN", "ROLE_ORG_ADMIN", "ADMIN", "ROLE_ADMIN" -> UserRole.ROLE_ORG_ADMIN;
            default -> throw new IllegalArgumentException("Unsupported role: " + raw);
        };
    }

    /** Maps legacy DB roles to FE-readable API/JWT role values. */
    public static UserRole toApiRole(UserRole role) {
        if (role == null) {
            return null;
        }
        if (role == UserRole.ROLE_USER) {
            return UserRole.ROLE_INVESTIGATOR;
        }
        if (role == UserRole.ROLE_ADMIN) {
            return UserRole.ROLE_ORG_ADMIN;
        }
        return role;
    }

    public static String toClientRole(UserRole role) {
        UserRole apiRole = toApiRole(role);
        if (apiRole == null) {
            return null;
        }
        return switch (apiRole) {
            case ROLE_INVESTIGATOR -> "INVESTIGATOR";
            case ROLE_REVIEWER -> "REVIEWER";
            case ROLE_ORG_ADMIN -> "ORG_ADMIN";
            case ROLE_USER -> "INVESTIGATOR";
            case ROLE_ADMIN -> "ORG_ADMIN";
        };
    }

    public static boolean isOrgAdmin(UserRole role) {
        return role == UserRole.ROLE_ADMIN || role == UserRole.ROLE_ORG_ADMIN;
    }

    public static boolean isReviewer(UserRole role) {
        return role == UserRole.ROLE_REVIEWER;
    }

    public static boolean isInvestigator(UserRole role) {
        return role == UserRole.ROLE_USER || role == UserRole.ROLE_INVESTIGATOR;
    }

    public static List<GrantedAuthority> toAuthorities(String roleClaim) {
        if (roleClaim == null || roleClaim.isBlank()) {
            return List.of();
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleClaim));
        if (UserRole.ROLE_ADMIN.name().equals(roleClaim) || UserRole.ROLE_ORG_ADMIN.name().equals(roleClaim)) {
            authorities.add(new SimpleGrantedAuthority(UserRole.ROLE_ADMIN.name()));
            authorities.add(new SimpleGrantedAuthority(UserRole.ROLE_ORG_ADMIN.name()));
        }
        return authorities;
    }
}
