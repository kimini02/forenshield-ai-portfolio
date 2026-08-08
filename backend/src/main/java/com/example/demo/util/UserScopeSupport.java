package com.example.demo.util;

import com.example.demo.domain.User;

public final class UserScopeSupport {

    private UserScopeSupport() {
    }

    public static boolean isSameOrganizationAndDepartment(User first, User second) {
        if (first == null || second == null || first.getOrganizationType() != second.getOrganizationType()) {
            return false;
        }

        String firstDepartment = normalizeDepartment(first.getDepartment());
        String secondDepartment = normalizeDepartment(second.getDepartment());
        return !firstDepartment.isBlank() && firstDepartment.equalsIgnoreCase(secondDepartment);
    }

    private static String normalizeDepartment(String department) {
        return department == null ? "" : department.trim();
    }
}
