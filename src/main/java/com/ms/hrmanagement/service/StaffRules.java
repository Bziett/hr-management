package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.User;
import java.util.List;

final class StaffRules {
    private StaffRules() {
    }

    static void validate(Employee candidate, String employeeId, String accountId,
                         List<Employee> employees, List<User> users) {
        String phone = normalizePhone(candidate.getPhone());
        for (Employee existing : employees) {
            if (sameId(existing.getId(), employeeId)) {
                continue;
            }
            checkPhone(phone, existing.getPhone());
            checkManager(candidate, existing.getRole(), existing.getDepartment());
            if (sameText(candidate.getEmail(), existing.getEmail())) {
                throw new IllegalArgumentException("Email is already used by another employee profile");
            }
        }
        for (User existing : users) {
            if (sameId(existing.getId(), accountId)) {
                continue;
            }
            if (sameText(candidate.getEmail(), existing.getEmail())) {
                throw new IllegalArgumentException("Email is already registered by another account");
            }
            if (List.of("HR", "MANAGER", "EMPLOYEE").contains(existing.getRole())) {
                checkPhone(phone, existing.getPhone());
                checkManager(candidate, existing.getRole(), existing.getDepartment());
            }
        }
    }

    static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("Phone number must contain digits");
        }
        return digits;
    }

    private static void checkPhone(String phone, String existingPhone) {
        if (!phone.isEmpty() && phone.equals(normalizePhone(existingPhone))) {
            throw new IllegalArgumentException("Phone number is already used by another staff member");
        }
    }

    private static void checkManager(Employee candidate, String role, String department) {
        if ("MANAGER".equalsIgnoreCase(candidate.getRole()) && "MANAGER".equalsIgnoreCase(role)
                && sameText(candidate.getDepartment(), department)) {
            throw new IllegalArgumentException("This department already has a Manager");
        }
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean sameId(String left, String right) {
        return left != null && left.equals(right);
    }
}
