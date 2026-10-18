package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.service.AuthService;
import com.ms.hrmanagement.service.EmployeeService;
import com.ms.hrmanagement.service.InternService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@Controller
@RequestMapping("/interns")
public class InternController {

    private final InternService internService;
    private final AuthService authService;
    private final EmployeeService employeeService;

    public InternController(
            InternService internService,
            AuthService authService,
            EmployeeService employeeService) {

        this.internService = internService;
        this.authService = authService;
        this.employeeService = employeeService;
    }

    // Intern list
    @GetMapping
    public String getAllInterns(
            Model model,
            Authentication authentication,
            @RequestParam(value = "department", required = false) String department)
            throws ExecutionException, InterruptedException {

        User currentUser = authService.getAuthenticatedUser(authentication);

        if (hasRole(authentication, "ROLE_MANAGER")) {
            model.addAttribute("interns", internService.getInternsByDepartment(departmentOf(currentUser)));
            model.addAttribute("selectedDepartment", departmentOf(currentUser));

        } else if (hasText(department)) {
            model.addAttribute("interns", internService.getInternsByDepartment(department));
            model.addAttribute("selectedDepartment", department);
        } else {
            model.addAttribute("interns", internService.getAllInterns());
            model.addAttribute("selectedDepartment", "");
        }

        return "intern/list";
    }

    // Add intern form
    @GetMapping("/add")
    public String showAddForm(
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        ensureAdminOrHr(authentication);
        model.addAttribute("intern", new Intern());
        model.addAttribute("mentorOptions", employeeService.getSeniorEmployeesByDepartment(""));
        return "intern/add";
    }

    // Add intern
    @PostMapping("/add")
    public String addIntern(
            @ModelAttribute Intern intern,
            Authentication authentication,
            Model model)
            throws ExecutionException, InterruptedException {

        ensureAdminOrHr(authentication);

        try {
            internService.createIntern(intern);

        } catch (IllegalArgumentException e) {
            model.addAttribute("intern", intern);
            model.addAttribute("mentorOptions", mentorOptionsFor(intern, authentication));
            model.addAttribute("error", e.getMessage());
            return "intern/add";
        }

        return "redirect:/interns";
    }

    // Intern details
    @GetMapping("/{id}")
    public String getInternById(
            @PathVariable String id,
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        Intern intern = internService.getInternById(id);

        if (intern == null) {
            return "redirect:/interns";
        }

        ensureCanViewIntern(intern, authentication);

        model.addAttribute("intern", intern);
        return "intern/detail";
    }

    // Edit intern form
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable String id,
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        Intern intern = internService.getInternById(id);

        if (intern == null) {
            return "redirect:/interns";
        }

        ensureCanEditIntern(intern, authentication);

        model.addAttribute("intern", intern);
        model.addAttribute("mentorOptions", mentorOptionsFor(intern, authentication));
        return "intern/edit";
    }

    // Update intern
    @PostMapping("/edit/{id}")
    public String updateIntern(
            @PathVariable String id,
            @ModelAttribute Intern intern,
            Authentication authentication,
            Model model)
            throws ExecutionException, InterruptedException {

        Intern existingIntern = internService.getInternById(id);

        if (existingIntern == null) {
            return "redirect:/interns";
        }

        ensureCanEditIntern(existingIntern, authentication);
        intern.setId(id);

        if (hasRole(authentication, "ROLE_MANAGER")) {
            intern.setDepartment(existingIntern.getDepartment());
        }

        try {
            internService.updateIntern(intern);

        } catch (IllegalArgumentException e) {
            model.addAttribute("intern", intern);
            model.addAttribute("mentorOptions", mentorOptionsFor(intern, authentication));
            model.addAttribute("error", e.getMessage());
            return "intern/edit";
        }

        return "redirect:/interns";
    }

    // Delete intern
    @PostMapping("/delete/{id}")
    public String deleteIntern(
            @PathVariable String id,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        ensureAdminOrHr(authentication);
        internService.deleteIntern(id);
        return "redirect:/interns";
    }

    private void ensureCanViewIntern(
            Intern intern,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);

        if (hasRole(authentication, "ROLE_MANAGER")
                && currentUser != null
                && internService.belongsToDepartment(intern, currentUser.getDepartment())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to view this intern profile");
    }

    private void ensureAdminOrHr(Authentication authentication) {

        if (!hasRole(authentication, "ROLE_ADMIN") && !hasRole(authentication, "ROLE_HR")) {
            throw new AccessDeniedException("Only Admin or HR can perform this action");
        }
    }

    private void ensureCanEditIntern(
            Intern intern,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);

        if (hasRole(authentication, "ROLE_MANAGER")
                && currentUser != null
                && internService.belongsToDepartment(intern, currentUser.getDepartment())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to update this intern profile");
    }

    private java.util.List<com.ms.hrmanagement.model.Employee> mentorOptionsFor(
            Intern intern,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        String department = intern != null ? intern.getDepartment() : "";

        if (hasRole(authentication, "ROLE_MANAGER")) {
            User currentUser = authService.getAuthenticatedUser(authentication);
            department = departmentOf(currentUser);
        }

        if (!hasText(department)) {
            return java.util.List.of();
        }

        return employeeService.getSeniorEmployeesByDepartment(department);
    }

    private String departmentOf(User user) {

        return user != null ? user.getDepartment() : "";
    }

    private boolean hasText(String value) {

        return value != null && !value.trim().isEmpty();
    }

    private boolean hasRole(Authentication authentication, String role) {

        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
