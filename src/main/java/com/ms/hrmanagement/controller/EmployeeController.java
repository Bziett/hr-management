package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.service.AuthService;
import com.ms.hrmanagement.service.EmployeeService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuthService authService;

    public EmployeeController(
            EmployeeService employeeService,
            AuthService authService) {

        this.employeeService = employeeService;
        this.authService = authService;
    }

    // =========================
    // EMPLOYEE LIST
    // =========================

    @GetMapping
    public String getAllEmployees(
            Model model,
            Authentication authentication,
            @RequestParam(value = "department", required = false) String department)
            throws ExecutionException, InterruptedException {

        authService.syncEmployeeProfilesForDirectoryAccounts();
        User currentUser = authService.getAuthenticatedUser(authentication);

        if (hasRole(authentication, "ROLE_MANAGER")) {
            model.addAttribute("employees", employeeService.getEmployeesByDepartment(departmentOf(currentUser)));
            model.addAttribute("selectedDepartment", departmentOf(currentUser));

        } else if (hasText(department)) {
            model.addAttribute("employees", employeeService.getEmployeesByDepartment(department));
            model.addAttribute("selectedDepartment", department);
        } else {
            model.addAttribute("employees", employeeService.getAllEmployees());
            model.addAttribute("selectedDepartment", "");
        }

        return "employee/list";
    }

    // =========================
    // ADD EMPLOYEE FORM
    // =========================

    @GetMapping("/add")
    public String showAddForm(
            Model model,
            Authentication authentication) {

        ensureAdminOrHr(authentication);

        model.addAttribute(
                "employee",
                new Employee()
        );

        return "employee/add";
    }

    // =========================
    // ADD EMPLOYEE
    // =========================

    @PostMapping("/add")
    public String addEmployee(
            @ModelAttribute Employee employee,
            @RequestParam String username,
            @RequestParam String password,
            Authentication authentication,
            Model model)
            throws ExecutionException, InterruptedException {

        ensureAdminOrHr(authentication);

        try {
            User account = new User();
            account.setUsername(username);
            account.setPassword(password);
            account.setFullName(employee.getFullName());
            account.setEmail(employee.getEmail());
            account.setRole(employee.getRole());
            account.setDepartment(employee.getDepartment());
            account.setPosition(employee.getPosition());
            account.setStatus(employee.getStatus());

            String accountId = authService.createManagerOrEmployeeAccount(account);
            User createdAccount = authService.getUserById(accountId);
            Employee createdProfile = employeeService.getEmployeeByUser(createdAccount);

            if (createdProfile != null) {
                if (hasText(employee.getPhone())) {
                    createdProfile.setPhone(employee.getPhone());
                }

                if (hasText(employee.getHireDate())) {
                    createdProfile.setHireDate(employee.getHireDate());
                }

                if (hasText(employee.getStatus())) {
                    createdProfile.setStatus(employee.getStatus());
                }

                employeeService.updateEmployee(createdProfile);
            }

        } catch (Exception e) {
            model.addAttribute("employee", employee);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            return "employee/add";
        }

        return "redirect:/employees";
    }

    // =========================
    // VIEW DETAILS
    // =========================

    @GetMapping("/{id}")
    public String getEmployeeById(
            @PathVariable String id,
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        Employee employee =
                employeeService.getEmployeeById(id);

        if (employee == null) {
            return "redirect:/employees";
        }

        ensureCanViewEmployee(employee, authentication);

        model.addAttribute(
                "employee",
                employee
        );

        return "employee/detail";
    }

    // =========================
    // EDIT EMPLOYEE FORM
    // =========================

    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable String id,
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        ensureAdminOrHr(authentication);

        Employee employee =
                employeeService.getEmployeeById(id);

        if (employee == null) {
            return "redirect:/employees";
        }

        ensureCanManageEmployeeProfile(employee, authentication);

        model.addAttribute(
                "employee",
                employee
        );

        return "employee/edit";
    }

    // =========================
    // UPDATE EMPLOYEE
    // =========================

    @PostMapping("/edit/{id}")
    public String updateEmployee(
            @PathVariable String id,
            @ModelAttribute Employee employee,
            Authentication authentication,
            Model model)
            throws ExecutionException, InterruptedException {

        ensureAdminOrHr(authentication);
        Employee existingEmployee = employeeService.getEmployeeById(id);

        if (existingEmployee == null) {
            return "redirect:/employees";
        }

        ensureCanManageEmployeeProfile(existingEmployee, authentication);
        employee.setId(id);

        try {
            employeeService.updateEmployee(employee);
            authService.syncAccountFromEmployeeProfile(employee, existingEmployee.getEmail());

        } catch (IllegalArgumentException e) {
            model.addAttribute("employee", employee);
            model.addAttribute("error", e.getMessage());
            return "employee/edit";
        }

        return "redirect:/employees";
    }

    // =========================
    // DELETE EMPLOYEE
    // =========================

    @PostMapping("/delete/{id}")
    public String deleteEmployee(
            @PathVariable String id,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        ensureAdminOrHr(authentication);
        Employee employee = employeeService.getEmployeeById(id);

        if (employee == null) {
            return "redirect:/employees";
        }

        ensureCanManageEmployeeProfile(employee, authentication);
        employeeService.deleteEmployee(id);
        authService.deleteAccountByEmail(employee.getEmail());

        return "redirect:/employees";
    }

    private void ensureCanViewEmployee(
            Employee employee,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);

        if (hasRole(authentication, "ROLE_MANAGER")
                && currentUser != null
                && employeeService.belongsToDepartment(employee, currentUser.getDepartment())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to view this employee profile");
    }

    private void ensureAdminOrHr(Authentication authentication) {

        if (!hasRole(authentication, "ROLE_ADMIN") && !hasRole(authentication, "ROLE_HR")) {
            throw new AccessDeniedException("Only Admin or HR can perform this action");
        }
    }

    private void ensureCanManageEmployeeProfile(
            Employee employee,
            Authentication authentication) {

        if (hasRole(authentication, "ROLE_ADMIN")) {
            return;
        }

        if (hasRole(authentication, "ROLE_HR")
                && !"HR".equalsIgnoreCase(employee.getRole())) {
            return;
        }

        throw new AccessDeniedException("Only Admin can update HR profiles");
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
