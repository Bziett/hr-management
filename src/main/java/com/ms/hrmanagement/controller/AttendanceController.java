package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.Attendance;
import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.service.AttendanceService;
import com.ms.hrmanagement.service.AuthService;
import com.ms.hrmanagement.service.EmployeeService;
import com.ms.hrmanagement.service.InternService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;
    private final InternService internService;
    private final AuthService authService;

    public AttendanceController(
            AttendanceService attendanceService,
            EmployeeService employeeService,
            InternService internService,
            AuthService authService) {

        this.attendanceService = attendanceService;
        this.employeeService = employeeService;
        this.internService = internService;
        this.authService = authService;
    }

    // =========================================================
    // SHOW ATTENDANCE LIST
    // =========================================================
    @GetMapping
    public String getAllAttendance(
            Model model,
            Authentication authentication,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "date", required = false) String date) throws Exception {

        User currentUser = authService.getAuthenticatedUser(authentication);
        List<Attendance> attendances;

        if (hasRole(authentication, "ROLE_MANAGER")) {
            List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
            List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));

            attendances = attendanceService.getAttendancesForDepartment(employees, interns);
            model.addAttribute("selectedDepartment", departmentOf(currentUser));

        } else if (hasRole(authentication, "ROLE_EMPLOYEE")) {
            Employee employee = employeeService.getEmployeeByUser(currentUser);
            attendances = attendanceService.getAttendancesForUser(currentUser, employee, null);
            model.addAttribute("selectedDepartment", departmentOf(currentUser));

        } else if (hasRole(authentication, "ROLE_INTERN")) {
            Intern intern = internService.getInternByUser(currentUser);
            attendances = attendanceService.getAttendancesForUser(currentUser, null, intern);
            model.addAttribute("selectedDepartment", departmentOf(currentUser));

        } else {
            attendances = attendanceService.getAttendancesByDepartment(department);
            model.addAttribute("selectedDepartment", department);
        }

        model.addAttribute("attendances", filterByDate(attendances, date));
        model.addAttribute("selectedDate", date);
        model.addAttribute("canCreateAttendance", isAttendanceCreator(authentication));
        model.addAttribute("canManageAttendance", isAttendanceManager(authentication));

        return "attendance/list";
    }

    // =========================================================
    // SHOW ADD ATTENDANCE FORM
    // =========================================================
    @GetMapping("/add")
    public String showAddForm(
            Model model,
            Authentication authentication) throws Exception {

        if (!isAttendanceCreator(authentication)) {
            throw new AccessDeniedException("Only Manager, Employee, or Intern accounts can create attendance records");
        }

        Attendance attendance = new Attendance();
        prepareSelfAttendance(attendance, authentication);

        model.addAttribute("attendance", attendance);
        model.addAttribute("selfAttendance", true);

        return "attendance/add";
    }

    // =========================================================
    // ADD ATTENDANCE
    // =========================================================
    @PostMapping("/add")
    public String addAttendance(
            @ModelAttribute Attendance attendance,
            Authentication authentication,
            Model model) throws Exception {

        if (!isAttendanceCreator(authentication)) {
            throw new AccessDeniedException("Only Manager, Employee, or Intern accounts can create attendance records");
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        Employee employee = employeeService.getEmployeeByUser(currentUser);
        Intern intern = internService.getInternByUser(currentUser);

        try {
            attendanceService.checkInForUser(attendance, currentUser, employee, intern);

        } catch (IllegalArgumentException e) {
            prepareSelfAttendance(attendance, authentication);
            model.addAttribute("attendance", attendance);
            model.addAttribute("selfAttendance", true);
            model.addAttribute("error", e.getMessage());
            return "attendance/add";
        }

        return "redirect:/attendance?checkedIn";
    }

    @PostMapping("/check-out")
    public String checkOut(
            Authentication authentication,
            RedirectAttributes redirectAttributes) throws Exception {

        if (!isAttendanceCreator(authentication)) {
            throw new AccessDeniedException("Only Manager, Employee, or Intern accounts can check out");
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        Employee employee = employeeService.getEmployeeByUser(currentUser);
        Intern intern = internService.getInternByUser(currentUser);

        try {
            attendanceService.checkOutForUser(currentUser, employee, intern);
            redirectAttributes.addFlashAttribute("attendanceSuccess", "Check-out recorded successfully.");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("attendanceError", e.getMessage());
        }

        return "redirect:/attendance";
    }

    @PostMapping("/submit-department")
    public String submitDepartmentAttendance(
            Authentication authentication) throws Exception {

        if (!hasRole(authentication, "ROLE_MANAGER")) {
            throw new AccessDeniedException("Only Managers can submit department attendance summaries");
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
        List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));

        attendanceService.submitDepartmentAttendanceToHr(employees, interns);

        return "redirect:/attendance?submitted";
    }

    // =========================================================
    // VIEW ATTENDANCE DETAILS
    // =========================================================
    @GetMapping("/{id}")
    public String getAttendanceById(
            @PathVariable String id,
            Model model,
            Authentication authentication) throws Exception {

        Attendance attendance =
                attendanceService.getAttendanceById(id);

        if (attendance == null) {
            return "redirect:/attendance";
        }

        ensureCanAccessAttendance(attendance, authentication);

        model.addAttribute("attendance", attendance);
        model.addAttribute("canManageAttendance", isAttendanceManager(authentication));

        return "attendance/detail";
    }

    // =========================================================
    // SHOW EDIT FORM
    // =========================================================
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable String id,
            Model model,
            Authentication authentication) throws Exception {

        Attendance attendance =
                attendanceService.getAttendanceById(id);

        if (attendance == null) {
            return "redirect:/attendance";
        }

        ensureCanManageAttendance(attendance, authentication);

        model.addAttribute("attendance", attendance);

        return "attendance/edit";
    }

    // =========================================================
    // UPDATE ATTENDANCE
    // =========================================================
    @PostMapping("/edit/{id}")
    public String updateAttendance(
            @PathVariable String id,
            @ModelAttribute Attendance attendance,
            Authentication authentication)
            throws Exception {

        Attendance existingAttendance = attendanceService.getAttendanceById(id);
        ensureCanManageAttendance(existingAttendance, authentication);

        attendance.setId(id);

        attendanceService.updateAttendance(attendance);

        return "redirect:/attendance";
    }

    // =========================================================
    // DELETE ATTENDANCE
    // =========================================================
    @PostMapping("/delete/{id}")
    public String deleteAttendance(
            @PathVariable String id,
            Authentication authentication) throws Exception {

        Attendance attendance = attendanceService.getAttendanceById(id);
        ensureCanManageAttendance(attendance, authentication);
        attendanceService.deleteAttendance(id);

        return "redirect:/attendance";
    }

    private void ensureCanAccessAttendance(
            Attendance attendance,
            Authentication authentication) throws Exception {

        if (attendance == null) {
            throw new AccessDeniedException("Attendance record not found");
        }

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
        List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));

        if (hasRole(authentication, "ROLE_MANAGER")
                && attendanceService.isAttendanceForDepartment(attendance, employees, interns)) {
            return;
        }

        if (hasRole(authentication, "ROLE_EMPLOYEE")) {
            Employee employee = employeeService.getEmployeeByUser(currentUser);

            if (attendanceService.isAttendanceForUser(attendance, currentUser, employee, null)) {
                return;
            }
        }

        if (hasRole(authentication, "ROLE_INTERN")) {
            Intern intern = internService.getInternByUser(currentUser);

            if (attendanceService.isAttendanceForUser(attendance, currentUser, null, intern)) {
                return;
            }
        }

        throw new AccessDeniedException("You do not have permission to view this attendance record");
    }

    private void ensureCanManageAttendance(
            Attendance attendance,
            Authentication authentication) throws Exception {

        if (attendance == null) {
            throw new AccessDeniedException("Attendance record not found");
        }

        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
            return;
        }

        User currentUser = authService.getAuthenticatedUser(authentication);
        List<Employee> employees = employeeService.getEmployeesByDepartment(departmentOf(currentUser));
        List<Intern> interns = internService.getInternsByDepartment(departmentOf(currentUser));

        if (hasRole(authentication, "ROLE_MANAGER")
                && attendanceService.isAttendanceForDepartment(attendance, employees, interns)) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to update this attendance record");
    }

    private void prepareSelfAttendance(
            Attendance attendance,
            Authentication authentication) throws Exception {

        User currentUser = authService.getAuthenticatedUser(authentication);
        Employee employee = employeeService.getEmployeeByUser(currentUser);
        Intern intern = internService.getInternByUser(currentUser);

        if (employee != null) {
            attendance.setEmployeeName(employee.getFullName());
            attendance.setDepartment(employee.getDepartment());
            return;
        }

        if (intern != null) {
            attendance.setEmployeeName(intern.getFullName());
            attendance.setDepartment(intern.getDepartment());
            return;
        }

        if (currentUser != null) {
            attendance.setEmployeeName(currentUser.getFullName());
            attendance.setDepartment(currentUser.getDepartment());
        }
    }

    private boolean isAttendanceCreator(Authentication authentication) {

        return hasRole(authentication, "ROLE_MANAGER")
                || hasRole(authentication, "ROLE_EMPLOYEE")
                || hasRole(authentication, "ROLE_INTERN");
    }

    private boolean isAttendanceManager(Authentication authentication) {

        return hasRole(authentication, "ROLE_ADMIN")
                || hasRole(authentication, "ROLE_HR")
                || hasRole(authentication, "ROLE_MANAGER");
    }

    private List<Attendance> filterByDate(List<Attendance> attendances, String date) {

        if (date == null || date.isBlank()) {
            return attendances;
        }

        return attendances.stream()
                .filter(attendance -> date.equals(attendance.getDate()))
                .toList();
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
