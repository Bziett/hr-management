package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.LeaveRequest;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.service.AuthService;
import com.ms.hrmanagement.service.EmployeeService;
import com.ms.hrmanagement.service.InternService;
import com.ms.hrmanagement.service.LeaveRequestService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Controller
@RequestMapping("/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final EmployeeService employeeService;
    private final InternService internService;
    private final AuthService authService;

    public LeaveRequestController(
            LeaveRequestService leaveRequestService,
            EmployeeService employeeService,
            InternService internService,
            AuthService authService) {

        this.leaveRequestService = leaveRequestService;
        this.employeeService = employeeService;
        this.internService = internService;
        this.authService = authService;
    }

    // Leave request list
    @GetMapping
    public String getAllLeaveRequests(
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        List<LeaveRequest> leaveRequests = getVisibleLeaveRequests(authentication);

        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("selfService", isPersonalOnly(authentication));
        model.addAttribute("canCreateLeaveRequest", isLeaveCreator(authentication));
        model.addAttribute("manageableLeaveRequestIds", getManageableLeaveRequestIds(leaveRequests, authentication));

        return "leave-request/list";
    }

    // Create leave request form
    @GetMapping("/add")
    public String showAddForm(
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        LeaveRequest leaveRequest = new LeaveRequest();

        if (!isLeaveCreator(authentication)) {
            throw new AccessDeniedException("HR and Admin review leave requests but do not create them here");
        }

        prepareSelfServiceLeaveRequest(leaveRequest, authentication);

        model.addAttribute("leaveRequest", leaveRequest);
        model.addAttribute("selfService", true);

        return "leave-request/add";
    }

    // Create leave request
    @PostMapping("/add")
    public String addLeaveRequest(
        @ModelAttribute LeaveRequest leaveRequest,
        Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (!isLeaveCreator(authentication)) {
            throw new AccessDeniedException("HR and Admin review leave requests but do not create them here");
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        Employee employee = employeeService.getEmployeeByUser(currentUser);
        Intern intern = internService.getInternByUser(currentUser);

        leaveRequestService.createLeaveRequestForUser(leaveRequest, currentUser, employee, intern);

        return "redirect:/leave-requests";
    }

    // View leave request details
    @GetMapping("/{id}")
    public String getLeaveRequestById(
            @PathVariable String id,
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        LeaveRequest leaveRequest =
                leaveRequestService.getLeaveRequestById(id);

        if (leaveRequest == null) {
            return "redirect:/leave-requests";
        }

        ensureCanViewLeaveRequest(leaveRequest, authentication);

        model.addAttribute("leaveRequest", leaveRequest);
        model.addAttribute("selfService", isPersonalOnly(authentication));
        model.addAttribute("canManageLeaveRequest", canManageLeaveRequest(leaveRequest, authentication));

        return "leave-request/detail";
    }

    // Edit leave request form
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable String id,
            Model model,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        LeaveRequest leaveRequest =
                leaveRequestService.getLeaveRequestById(id);

        if (leaveRequest == null) {
            return "redirect:/leave-requests";
        }

        ensureCanManageLeaveRequest(leaveRequest, authentication);

        model.addAttribute("leaveRequest", leaveRequest);

        return "leave-request/edit";
    }

    // Update leave request
    @PostMapping("/edit/{id}")
    public String updateLeaveRequest(
            @PathVariable String id,
            @ModelAttribute LeaveRequest leaveRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        LeaveRequest existingLeaveRequest = leaveRequestService.getLeaveRequestById(id);
        ensureCanManageLeaveRequest(existingLeaveRequest, authentication);

        leaveRequest.setId(id);

        leaveRequestService.updateLeaveRequest(leaveRequest);

        return "redirect:/leave-requests";
    }

    // Delete leave request
    @PostMapping("/delete/{id}")
    public String deleteLeaveRequest(
            @PathVariable String id,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        LeaveRequest leaveRequest = leaveRequestService.getLeaveRequestById(id);
        ensureCanManageLeaveRequest(leaveRequest, authentication);
        leaveRequestService.deleteLeaveRequest(id);

        return "redirect:/leave-requests";
    }

    private List<LeaveRequest> getVisibleLeaveRequests(Authentication authentication)
            throws ExecutionException, InterruptedException {

        User currentUser = authService.getAuthenticatedUser(authentication);

        if (hasRole(authentication, "ROLE_MANAGER")) {
            List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
            List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));

            return leaveRequestService.getLeaveRequestsForDepartment(employees, interns);
        }

        if (isPersonalOnly(authentication)) {
            Employee employee = employeeService.getEmployeeByUser(currentUser);
            Intern intern = internService.getInternByUser(currentUser);

            return leaveRequestService.getLeaveRequestsForUser(currentUser, employee, intern);
        }

        return leaveRequestService.getAllLeaveRequests();
    }

    private void prepareSelfServiceLeaveRequest(
            LeaveRequest leaveRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (!isLeaveCreator(authentication)) {
            return;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        Employee employee = employeeService.getEmployeeByUser(currentUser);
        Intern intern = internService.getInternByUser(currentUser);

        if (employee != null) {
            leaveRequest.setEmployeeId(employee.getId());
            leaveRequest.setEmployeeName(employee.getFullName());

        } else if (intern != null) {
            leaveRequest.setEmployeeId(intern.getId());
            leaveRequest.setEmployeeName(intern.getFullName());

        } else if (currentUser != null) {
            leaveRequest.setEmployeeId(currentUser.getId());
            leaveRequest.setEmployeeName(currentUser.getFullName());
        }

        leaveRequest.setStatus("PENDING");
    }

    private void ensureCanViewLeaveRequest(
            LeaveRequest leaveRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (leaveRequest == null) {
            throw new AccessDeniedException("Leave request not found");
        }

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);

        if (hasRole(authentication, "ROLE_MANAGER")) {
            List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
            List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));

            if (leaveRequestService.isLeaveRequestForDepartment(leaveRequest, employees, interns)) {
                return;
            }
        }

        if (isPersonalOnly(authentication)) {
            Employee employee = employeeService.getEmployeeByUser(currentUser);
            Intern intern = internService.getInternByUser(currentUser);

            if (leaveRequestService.isLeaveRequestForUser(leaveRequest, currentUser, employee, intern)) {
                return;
            }
        }

        throw new AccessDeniedException("You do not have permission to view this leave request");
    }

    private void ensureCanManageLeaveRequest(
            LeaveRequest leaveRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (leaveRequest == null) {
            throw new AccessDeniedException("Leave request not found");
        }

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return;
        }

        if (hasRole(authentication, "ROLE_MANAGER")) {
            User currentUser = authService.getAuthenticatedUser(authentication);
            List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
            List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));
            Employee managerProfile = employeeService.getEmployeeByUser(currentUser);

            if (leaveRequestService.isLeaveRequestForDepartment(leaveRequest, employees, interns)
                    && !leaveRequestService.isLeaveRequestForUser(leaveRequest, currentUser, managerProfile, null)) {
                return;
            }
        }

        throw new AccessDeniedException("You do not have permission to process this leave request");
    }

    private List<String> getManageableLeaveRequestIds(
            List<LeaveRequest> leaveRequests,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        List<String> ids = new ArrayList<>();

        for (LeaveRequest leaveRequest : leaveRequests) {
            if (canManageLeaveRequest(leaveRequest, authentication)) {
                ids.add(leaveRequest.getId());
            }
        }

        return ids;
    }

    private boolean canManageLeaveRequest(
            LeaveRequest leaveRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (leaveRequest == null) {
            return false;
        }

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return true;
        }

        if (!hasRole(authentication, "ROLE_MANAGER")) {
            return false;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
        List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));
        Employee managerProfile = employeeService.getEmployeeByUser(currentUser);

        return leaveRequestService.isLeaveRequestForDepartment(leaveRequest, employees, interns)
                && !leaveRequestService.isLeaveRequestForUser(leaveRequest, currentUser, managerProfile, null);
    }

    private boolean isPersonalOnly(Authentication authentication) {

        return hasRole(authentication, "ROLE_EMPLOYEE") || hasRole(authentication, "ROLE_INTERN");
    }

    private boolean isLeaveCreator(Authentication authentication) {

        return hasRole(authentication, "ROLE_MANAGER")
                || hasRole(authentication, "ROLE_EMPLOYEE")
                || hasRole(authentication, "ROLE_INTERN");
    }

    private String departmentOf(User user) {

        return user != null ? user.getDepartment() : "";
    }

    private boolean hasRole(Authentication authentication, String role) {

        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
