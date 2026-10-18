package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {

    private final AuthService authService;

    public AccountController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/admin/accounts")
    public String adminAccounts(Model model) throws Exception {

        model.addAttribute("user", new User());
        model.addAttribute("users", authService.getAdminHrManagerAccounts());

        return "admin/accounts";
    }

    @PostMapping("/admin/accounts/hr")
    public String createHrAccount(
            @ModelAttribute User user,
            Model model) throws Exception {

        try {
            authService.createHrAccount(user);
            return "redirect:/admin/accounts?success";

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("user", user);
            model.addAttribute("users", authService.getAdminHrManagerAccounts());
            return "admin/accounts";
        }
    }

    @GetMapping("/hr/accounts")
    public String hrAccounts() {

        return "redirect:/employees/add";
    }

    @PostMapping("/hr/accounts/staff")
    public String createStaffAccount() {

        return "redirect:/employees/add";
    }

    @PostMapping("/account/password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            Authentication authentication,
            RedirectAttributes redirectAttributes) throws Exception {

        User currentUser = authService.getAuthenticatedUser(authentication);

        try {
            authService.changePassword(currentUser, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("passwordChanged", "Password updated successfully.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("passwordError", e.getMessage());
        }

        return "redirect:" + dashboardPath(currentUser);
    }

    private String dashboardPath(User user) {

        if (user == null || user.getRole() == null) {
            return "/login";
        }

        return switch (user.getRole().replace("ROLE_", "").trim().toUpperCase()) {
            case "ADMIN" -> "/admin/dashboard";
            case "HR" -> "/hr/dashboard";
            case "MANAGER" -> "/manager/dashboard";
            case "EMPLOYEE" -> "/employee/dashboard";
            case "INTERN" -> "/intern/dashboard";
            default -> "/portal";
        };
    }
}
