package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.*;
import com.ms.hrmanagement.service.*;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/attendance")
public class AttendanceController {
    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;
    private final InternService internService;
    private final AuthService authService;
    private final AttendancePhotoService photoService;

    public AttendanceController(AttendanceService attendanceService, EmployeeService employeeService,
            InternService internService, AuthService authService, AttendancePhotoService photoService) {
        this.attendanceService = attendanceService;
        this.employeeService = employeeService;
        this.internService = internService;
        this.authService = authService;
        this.photoService = photoService;
    }

    @GetMapping
    public String list(Model model, Authentication authentication,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "all") String view) throws Exception {
        User user = authService.getAuthenticatedUser(authentication);
        boolean missingOnly = "missing-check-out".equals(view);
        String selectedDate = date == null || date.isBlank() ? (missingOnly ? "" : attendanceService.today()) : date;
        try { if (!selectedDate.isBlank()) java.time.LocalDate.parse(selectedDate); }
        catch (java.time.DateTimeException exception) {
            model.addAttribute("attendanceError", "Select a valid date");
            selectedDate = attendanceService.today();
        }
        final String filterDate = selectedDate;
        List<Attendance> rows;
        if (hasRole(authentication, "MANAGER")) {
            if (user == null || user.getDepartment() == null || user.getDepartment().isBlank()) {
                throw new AccessDeniedException("A managed department must be assigned before viewing attendance");
            }
            rows = departmentRows(user);
            department = user != null ? user.getDepartment() : "";
            var employees = employeeService.getEmployeesByDepartment(department);
            var interns = internService.getInternsByDepartment(department);
            var presentIds = rows.stream().filter(row -> filterDate.equals(row.getDate()))
                    .map(Attendance::getEmployeeId).collect(java.util.stream.Collectors.toSet());
            var missing = new java.util.ArrayList<String>();
            employees.stream().filter(person -> !"HR".equals(person.getRole()) && !"ADMIN".equals(person.getRole()))
                    .filter(person -> "ACTIVE".equalsIgnoreCase(person.getStatus()))
                    .filter(person -> !presentIds.contains(person.getId())).forEach(person -> missing.add(person.getFullName()));
            interns.stream().filter(person -> "ACTIVE".equalsIgnoreCase(person.getStatus()) || "ACTIVE_INTERNSHIP".equalsIgnoreCase(person.getStatus()))
                    .filter(person -> !presentIds.contains(person.getId())).forEach(person -> missing.add(person.getFullName()));
            model.addAttribute("missingCheckIns", missing);
        } else if (hasRole(authentication, "EMPLOYEE") || hasRole(authentication, "INTERN")) {
            rows = attendanceService.getAttendancesForUser(user, employeeService.getEmployeeByUser(user), internService.getInternByUser(user));
        } else if (hasRole(authentication, "HR")) {
            rows = attendanceService.getAttendancesForHr();
        } else if (hasRole(authentication, "ADMIN")) {
            rows = attendanceService.getAllAttendances();
        } else {
            throw new AccessDeniedException("Attendance access is not allowed");
        }
        final String selectedDepartment = department;
        var visibleRows = rows.stream()
                .filter(row -> !isOffice(authentication) || selectedDepartment == null || selectedDepartment.isBlank()
                        || selectedDepartment.equalsIgnoreCase(row.getDepartment())).toList();
        model.addAttribute("missingCheckOutIds", visibleRows.stream().filter(attendanceService::isMissingCheckOut).map(Attendance::getId).toList());
        model.addAttribute("attendances", visibleRows.stream()
                .filter(row -> filterDate.isBlank() || filterDate.equals(row.getDate()))
                .filter(row -> !missingOnly || attendanceService.isMissingCheckOut(row)).toList());
        model.addAttribute("selectedView", missingOnly ? "missing-check-out" : "all");
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedDepartment", department);
        model.addAttribute("canCreateAttendance", isCreator(authentication));
        model.addAttribute("canManageAttendance", isOffice(authentication));
        model.addAttribute("today", attendanceService.today());
        return "attendance/list";
    }

    @GetMapping("/add")
    public String addForm(Model model, Authentication authentication) throws Exception {
        requireCreator(authentication);
        User user = authService.getAuthenticatedUser(authentication);
        var employee = employeeService.getEmployeeByUser(user);
        var intern = internService.getInternByUser(user);
        var records = attendanceService.getAttendancesForUser(user, employee, intern);
        var existing = records.stream().filter(row -> attendanceService.today().equals(row.getDate())).findFirst();
        if (existing.isPresent()) return "redirect:/attendance/" + existing.get().getId();
        Attendance attendance = new Attendance();
        attendance.setEmployeeName(employee != null ? employee.getFullName() : intern != null ? intern.getFullName() : user.getFullName());
        attendance.setDepartment(employee != null ? employee.getDepartment() : intern != null ? intern.getDepartment() : user.getDepartment());
        attendance.setDate(attendanceService.today());
        model.addAttribute("attendance", attendance);
        return "attendance/add";
    }

    @PostMapping("/add")
    public String checkIn(Authentication authentication, RedirectAttributes flash) throws Exception {
        requireCreator(authentication);
        User user = authService.getAuthenticatedUser(authentication);
        try {
            String id = attendanceService.checkInForUser(new Attendance(), user,
                    employeeService.getEmployeeByUser(user), internService.getInternByUser(user));
            flash.addFlashAttribute("attendanceSuccess", "Check-in time recorded. Submit your photo for today.");
            return "redirect:/attendance/" + id;
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("attendanceError", exception.getMessage());
            return "redirect:/attendance/add";
        }
    }

    @PostMapping("/{id}/photo")
    public String uploadPhoto(@PathVariable String id, @RequestParam MultipartFile photo,
            Authentication authentication, RedirectAttributes flash) throws Exception {
        requireCreator(authentication);
        Attendance row = attendanceService.getAttendanceById(id);
        ensureAccess(row, authentication);
        User user = authService.getAuthenticatedUser(authentication);
        var employee = employeeService.getEmployeeByUser(user);
        var intern = internService.getInternByUser(user);
        if (!attendanceService.isAttendanceForUser(row, user, employee, intern)) throw new AccessDeniedException("Only the owner can submit a photo");
        try {
            attendanceService.submitPhoto(id, user, employee, intern, photoService.compress(photo));
            flash.addFlashAttribute("attendanceSuccess", "Photo submitted. Your check-in is awaiting Manager confirmation.");
        } catch (IllegalArgumentException | java.io.IOException exception) {
            flash.addFlashAttribute("attendanceError", exception instanceof java.io.IOException
                    ? "Unable to read this photo. Choose a valid JPEG or PNG file." : exception.getMessage());
        }
        return "redirect:/attendance/" + id;
    }

    @GetMapping("/{id}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> photo(@PathVariable String id, Authentication authentication) throws Exception {
        ensureAccess(attendanceService.getAttendanceById(id), authentication);
        byte[] image = attendanceService.getPhoto(id);
        if (image == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff").body(image);
    }

    @PostMapping("/check-out")
    public String checkOut(Authentication authentication, RedirectAttributes flash) throws Exception {
        requireCreator(authentication);
        User user = authService.getAuthenticatedUser(authentication);
        try {
            attendanceService.checkOutForUser(user, employeeService.getEmployeeByUser(user), internService.getInternByUser(user));
            flash.addFlashAttribute("attendanceSuccess", "Check-out recorded successfully.");
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("attendanceError", exception.getMessage());
        }
        return "redirect:/attendance";
    }

    @PostMapping("/submit-department")
    public String submitDepartment(Authentication authentication, @RequestParam String date, RedirectAttributes flash) throws Exception {
        if (!hasRole(authentication, "MANAGER")) throw new AccessDeniedException("Only Managers can submit a summary");
        try {
            int count = attendanceService.submitDepartmentAttendanceToHr(authService.getAuthenticatedUser(authentication), date);
            flash.addFlashAttribute("attendanceSuccess", count + " check-in(s) submitted to HR for " + date);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            flash.addFlashAttribute("attendanceError", exception instanceof java.time.DateTimeException ? "Select a valid date" : exception.getMessage());
            if (exception instanceof java.time.DateTimeException) return "redirect:/attendance";
        }
        return "redirect:/attendance?date=" + java.net.URLEncoder.encode(date, java.nio.charset.StandardCharsets.UTF_8);
    }

    @GetMapping("/supplement")
    public String supplementForm(Model model, Authentication authentication) throws Exception {
        requireOffice(authentication);
        model.addAttribute("employees", employeeService.getAllEmployees().stream()
                .filter(person -> "MANAGER".equals(person.getRole()) || "EMPLOYEE".equals(person.getRole())).toList());
        model.addAttribute("interns", internService.getAllInterns());
        model.addAttribute("today", attendanceService.today());
        return "attendance/supplement";
    }

    @PostMapping("/supplement")
    public String supplement(@RequestParam String personId, @RequestParam String personType,
            @RequestParam String date, @RequestParam String checkIn, @RequestParam(required = false) String checkOut,
            @RequestParam String correctionReason, Authentication authentication, RedirectAttributes flash) throws Exception {
        requireOffice(authentication);
        try {
            Employee employee = "EMPLOYEE".equals(personType) ? employeeService.getEmployeeById(personId) : null;
            Intern intern = "INTERN".equals(personType) ? internService.getInternById(personId) : null;
            if (employee != null && !List.of("EMPLOYEE", "MANAGER").contains(employee.getRole())) {
                throw new IllegalArgumentException("Choose an Employee, Manager, or Intern");
            }
            Attendance row = new Attendance();
            row.setDate(date);
            row.setCheckIn(checkIn);
            row.setCheckOut(checkOut);
            row.setCorrectionReason(correctionReason);
            attendanceService.createManual(row, employee, intern, authentication.getName());
            flash.addFlashAttribute("attendanceSuccess", "Supplementary check-in recorded");
            return "redirect:/attendance?date=" + java.net.URLEncoder.encode(date, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            flash.addFlashAttribute("attendanceError", exception instanceof java.time.DateTimeException ? "Select a valid date and time" : exception.getMessage());
            return "redirect:/attendance/supplement";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model, Authentication authentication) throws Exception {
        Attendance row = attendanceService.getAttendanceById(id);
        ensureAccess(row, authentication);
        User user = authService.getAuthenticatedUser(authentication);
        model.addAttribute("attendance", row);
        model.addAttribute("canManageAttendance", isOffice(authentication));
        model.addAttribute("missingCheckOut", attendanceService.isMissingCheckOut(row));
        model.addAttribute("canUploadPhoto", isCreator(authentication) && attendanceService.today().equals(row.getDate())
                && "AWAITING_PHOTO".equals(row.getReviewStatus()) && attendanceService.isAttendanceForUser(row, user,
                        employeeService.getEmployeeByUser(user), internService.getInternByUser(user)));
        return "attendance/detail";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable String id, Model model, Authentication authentication) throws Exception {
        requireOffice(authentication);
        Attendance row = attendanceService.getAttendanceById(id);
        ensureAccess(row, authentication);
        model.addAttribute("attendance", row);
        return "attendance/edit";
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable String id, @RequestParam String checkIn, @RequestParam String checkOut,
            @RequestParam String correctionReason, Authentication authentication, RedirectAttributes flash) throws Exception {
        requireOffice(authentication);
        ensureAccess(attendanceService.getAttendanceById(id), authentication);
        try {
            attendanceService.correctTimes(id, checkIn, checkOut, correctionReason, authentication.getName());
            flash.addFlashAttribute("attendanceSuccess", "Attendance correction saved");
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            flash.addFlashAttribute("attendanceError", "Invalid correction: " + exception.getMessage());
        }
        return "redirect:/attendance/" + id;
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable String id, Authentication authentication) throws Exception {
        requireOffice(authentication);
        ensureAccess(attendanceService.getAttendanceById(id), authentication);
        attendanceService.deleteAttendance(id);
        return "redirect:/attendance";
    }

    private List<Attendance> departmentRows(User user) throws Exception {
        if (user == null || user.getDepartment() == null || user.getDepartment().isBlank()) return List.of();
        return attendanceService.getAttendancesByDepartment(user.getDepartment());
    }

    private void ensureAccess(Attendance row, Authentication authentication) throws Exception {
        if (row == null) throw new AccessDeniedException("Attendance record not found");
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "HR")) {
            if (attendanceService.isVisibleToHr(row)) return;
            throw new AccessDeniedException("This attendance has not been submitted by the Manager");
        }
        User user = authService.getAuthenticatedUser(authentication);
        if (user == null) throw new AccessDeniedException("Sign in to continue");
        if (hasRole(authentication, "MANAGER") && user.getDepartment() != null && !user.getDepartment().isBlank()
                && user.getDepartment().equalsIgnoreCase(row.getDepartment())) return;
        if (isCreator(authentication) && attendanceService.isAttendanceForUser(row, user,
                employeeService.getEmployeeByUser(user), internService.getInternByUser(user))) return;
        throw new AccessDeniedException("You do not have permission to view this attendance");
    }

    private void requireOffice(Authentication authentication) {
        if (!isOffice(authentication)) throw new AccessDeniedException("Only Admin and HR may correct attendance");
    }
    private void requireCreator(Authentication authentication) {
        if (!isCreator(authentication)) throw new AccessDeniedException("Only staff can check in or out");
    }
    private boolean isOffice(Authentication authentication) { return hasRole(authentication, "ADMIN") || hasRole(authentication, "HR"); }
    private boolean isCreator(Authentication authentication) { return hasRole(authentication, "MANAGER") || hasRole(authentication, "EMPLOYEE") || hasRole(authentication, "INTERN"); }
    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }
}
