package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.LeaveRequest;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.service.AttendanceService;
import com.ms.hrmanagement.service.AuthService;
import com.ms.hrmanagement.service.EmployeeService;
import com.ms.hrmanagement.service.InternService;
import com.ms.hrmanagement.service.LeaveRequestService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final EmployeeService employeeService;
    private final InternService internService;
    private final AttendanceService attendanceService;
    private final LeaveRequestService leaveRequestService;
    private final AuthService authService;

    public DashboardController(
            EmployeeService employeeService,
            InternService internService,
            AttendanceService attendanceService,
            LeaveRequestService leaveRequestService,
            AuthService authService) {

        this.employeeService = employeeService;
        this.internService = internService;
        this.attendanceService = attendanceService;
        this.leaveRequestService = leaveRequestService;
        this.authService = authService;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {

        if (hasRole(authentication, "ROLE_ADMIN")) {
            return "redirect:/admin/dashboard";
        }

        if (hasRole(authentication, "ROLE_HR")) {
            return "redirect:/hr/dashboard";
        }

        if (hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/manager/dashboard";
        }

        if (hasRole(authentication, "ROLE_EMPLOYEE")) {
            return "redirect:/employee/dashboard";
        }

        if (hasRole(authentication, "ROLE_INTERN")) {
            return "redirect:/intern/dashboard";
        }

        return "redirect:/portal";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) throws Exception {

        authService.syncEmployeeProfilesForDirectoryAccounts();

        var employees = employeeService.getAllEmployees();
        var interns = internService.getAllInterns();
        var attendances = attendanceService.getAllAttendances();
        var leaveRequests = leaveRequestService.getAllLeaveRequests();

        addSummary(model, employees, interns, attendances, leaveRequests);
        model.addAttribute("accountCount", authService.getAllUsers().size());
        model.addAttribute("pendingLeaveCount", countByStatus(leaveRequests, "PENDING"));

        return "admin/dashboard";
    }

    @GetMapping("/hr/dashboard")
    public String hrDashboard(Model model) throws Exception {

        authService.syncEmployeeProfilesForDirectoryAccounts();

        var employees = employeeService.getAllEmployees();
        var interns = internService.getAllInterns();
        var attendances = attendanceService.getAttendancesForHr();
        var leaveRequests = leaveRequestService.getAllLeaveRequests();

        addSummary(model, employees, interns, attendances, leaveRequests);
        model.addAttribute("visibleAccountCount", authService.getUsersVisibleToHr().size());
        model.addAttribute("pendingLeaveCount", countByStatus(leaveRequests, "PENDING"));

        return "hr/dashboard";
    }

    @GetMapping("/manager/dashboard")
    public String managerDashboard(Model model, Authentication authentication) throws Exception {

        User currentUser = authService.getAuthenticatedUser(authentication);
        String department = departmentOf(currentUser);

        authService.syncEmployeeProfilesForDirectoryAccounts();

        List<Employee> employees = employeeService.getEmployeesByDepartment(department);
        List<Intern> interns = internService.getInternsByDepartment(department);
        var attendances = attendanceService.getAttendancesForDepartment(employees, interns);
        List<LeaveRequest> leaveRequests = leaveRequestService.getLeaveRequestsForDepartment(employees, interns);

        addSummary(model, employees, interns, attendances, leaveRequests);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("managedDepartment", department);
        model.addAttribute("pendingLeaveCount", countByStatus(leaveRequests, "PENDING"));

        return "manager/dashboard";
    }

    @GetMapping("/employee/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) throws Exception {

        User currentUser = authService.getAuthenticatedUser(authentication);
        Employee employee = employeeService.getEmployeeByUser(currentUser);
        var attendances = attendanceService.getAttendancesForUser(currentUser, employee, null);
        var leaveRequests = leaveRequestService.getLeaveRequestsForUser(currentUser, employee, null);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("employee", employee);
        model.addAttribute("attendances", attendances);
        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("attendanceCount", attendances.size());
        model.addAttribute("leaveRequestCount", leaveRequests.size());
        model.addAttribute("pendingLeaveCount", countByStatus(leaveRequests, "PENDING"));

        return "employee/dashboard";
    }

    @GetMapping("/intern/dashboard")
    public String internDashboard(Model model, Authentication authentication) throws Exception {

        User currentUser = authService.getAuthenticatedUser(authentication);
        Intern intern = internService.getInternByUser(currentUser);
        var attendances = attendanceService.getAttendancesForUser(currentUser, null, intern);
        var leaveRequests = leaveRequestService.getLeaveRequestsForUser(currentUser, null, intern);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("intern", intern);
        model.addAttribute("attendances", attendances);
        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("attendanceCount", attendances.size());
        model.addAttribute("leaveRequestCount", leaveRequests.size());
        model.addAttribute("pendingLeaveCount", countByStatus(leaveRequests, "PENDING"));

        return "intern/dashboard";
    }

    @GetMapping("/portal")
    public String portal(Authentication authentication) {

        return home(authentication);
    }

    private void addSummary(
            Model model,
            List<Employee> employees,
            List<Intern> interns,
            List<?> attendances,
            List<LeaveRequest> leaveRequests) {

        model.addAttribute("employees", employees);
        model.addAttribute("interns", interns);
        model.addAttribute("attendances", attendances);
        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("employeeCount", employeeService.countEmployeesAndManagers(employees));
        model.addAttribute("internCount", interns.size());
        model.addAttribute("attendanceCount", attendances.size());
        model.addAttribute("leaveRequestCount", leaveRequests.size());
    }

    private long countByStatus(List<LeaveRequest> leaveRequests, String status) {

        return leaveRequests.stream()
                .filter(leaveRequest -> status.equalsIgnoreCase(leaveRequest.getStatus()))
                .count();
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
