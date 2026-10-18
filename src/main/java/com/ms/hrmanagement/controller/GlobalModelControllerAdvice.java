package com.ms.hrmanagement.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalModelControllerAdvice {

    @ModelAttribute("currentRole")
    public String currentRole(Authentication authentication) {

        if (authentication == null) {
            return "";
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("");
    }

    @ModelAttribute("departments")
    public List<String> departments() {

        return List.of(
                "Engineering",
                "Product",
                "QA",
                "Design",
                "HR",
                "Finance",
                "Sales",
                "Marketing"
        );
    }

    @ModelAttribute("staffRoles")
    public List<String> staffRoles() {

        return List.of("MANAGER", "EMPLOYEE");
    }

    @ModelAttribute("positions")
    public List<String> positions() {

        return List.of("Senior", "Junior");
    }
}
