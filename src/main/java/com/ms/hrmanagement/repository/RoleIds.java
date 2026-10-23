package com.ms.hrmanagement.repository;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class RoleIds {
    private static final Set<String> ROLES = Set.of("ADMIN", "HR", "MANAGER", "EMPLOYEE", "INTERN");

    private RoleIds() {
    }

    public static String newId(String role) {
        return normalize(role) + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    public static String forRole(String id, String role) {
        String prefix = normalize(role) + "-";
        return id != null && id.startsWith(prefix) ? id : newId(role);
    }

    private static String normalize(String role) {
        String normalized = role == null ? "EMPLOYEE" : role.replace("ROLE_", "").trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid account role");
        }
        return normalized;
    }
}
