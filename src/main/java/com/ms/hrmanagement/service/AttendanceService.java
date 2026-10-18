package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Attendance;
import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    // =========================
    // CREATE
    // =========================

    public String createAttendance(Attendance attendance)
            throws ExecutionException, InterruptedException {

        if (attendance == null) {
            throw new IllegalArgumentException("Invalid attendance information");
        }

        if (!hasText(attendance.getStatus())) {
            attendance.setStatus("PRESENT");
        }

        return attendanceRepository.save(attendance);
    }

    public String createAttendanceForUser(
            Attendance attendance,
            User user,
            Employee employee,
            Intern intern)
            throws ExecutionException, InterruptedException {

        return checkInForUser(attendance, user, employee, intern);
    }

    public String checkInForUser(
            Attendance attendance,
            User user,
            Employee employee,
            Intern intern)
            throws ExecutionException, InterruptedException {

        if (user == null) {
            throw new IllegalArgumentException("Unable to identify the current account");
        }

        assignAttendanceOwner(attendance, user, employee, intern);

        if (!hasText(attendance.getDate())) {
            attendance.setDate(LocalDate.now().toString());
        }

        Attendance existingAttendance = findAttendanceForDate(user, employee, intern, attendance.getDate());

        if (existingAttendance != null && hasText(existingAttendance.getCheckIn())) {
            throw new IllegalArgumentException("You have already checked in for this date");
        }

        if (!hasText(attendance.getCheckIn())) {
            attendance.setCheckIn(LocalTime.now().withSecond(0).withNano(0).toString());
        }

        String role = normalizeRole(user.getRole());
        attendance.setSubmittedByRole(role);
        attendance.setReviewStatus("SUBMITTED_TO_HR");

        if (!hasText(attendance.getStatus())) {
            attendance.setStatus("PRESENT");
        }

        if (existingAttendance != null) {
            existingAttendance.setCheckIn(attendance.getCheckIn());
            existingAttendance.setStatus(attendance.getStatus());
            existingAttendance.setSubmittedByRole(attendance.getSubmittedByRole());
            existingAttendance.setReviewStatus(attendance.getReviewStatus());
            existingAttendance.setNote(attendance.getNote());
            updateAttendance(existingAttendance);
            return existingAttendance.getId();
        }

        return createAttendance(attendance);
    }

    public void checkOutForUser(
            User user,
            Employee employee,
            Intern intern)
            throws ExecutionException, InterruptedException {

        if (user == null) {
            throw new IllegalArgumentException("Unable to identify the current account");
        }

        Attendance attendance = findAttendanceForDate(user, employee, intern, LocalDate.now().toString());

        if (attendance == null) {
            throw new IllegalArgumentException("Please check in before checking out");
        }

        if (!hasText(attendance.getCheckIn())) {
            throw new IllegalArgumentException("Please check in before checking out");
        }

        if (hasText(attendance.getCheckOut())) {
            throw new IllegalArgumentException("You have already checked out today");
        }

        attendance.setCheckOut(LocalTime.now().withSecond(0).withNano(0).toString());
        attendance.setReviewStatus("SUBMITTED_TO_HR");

        if (!hasText(attendance.getStatus())) {
            attendance.setStatus("PRESENT");
        }

        updateAttendance(attendance);
    }

    // =========================
    // READ - BY ID
    // =========================

    public Attendance getAttendanceById(String id)
            throws ExecutionException, InterruptedException {

        return attendanceRepository.findById(id);
    }

    // =========================
    // READ - ALL
    // =========================

    public List<Attendance> getAllAttendances()
            throws ExecutionException, InterruptedException {

        return attendanceRepository.findAll();
    }

    public List<Attendance> getAttendancesForDepartment(
            List<Employee> employees,
            List<Intern> interns)
            throws ExecutionException, InterruptedException {

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> departments = new HashSet<>();

        employees.forEach(employee -> {
            addIdentity(ids, names, employee.getId(), employee.getFullName());
            addDepartment(departments, employee.getDepartment());
        });
        interns.forEach(intern -> {
            addIdentity(ids, names, intern.getId(), intern.getFullName());
            addDepartment(departments, intern.getDepartment());
        });

        return getAllAttendances().stream()
                .filter(attendance -> matchesDepartment(attendance, departments)
                        || matchesIdentity(attendance, ids, names))
                .toList();
    }

    public List<Attendance> getAttendancesByDepartment(String department)
            throws ExecutionException, InterruptedException {

        if (!hasText(department)) {
            return getAllAttendances();
        }

        return getAllAttendances().stream()
                .filter(attendance -> sameText(attendance.getDepartment(), department))
                .toList();
    }

    public void submitDepartmentAttendanceToHr(
            List<Employee> employees,
            List<Intern> interns)
            throws ExecutionException, InterruptedException {

        List<Attendance> attendances = getAttendancesForDepartment(employees, interns);

        for (Attendance attendance : attendances) {
            if (!"REVIEWED".equalsIgnoreCase(attendance.getReviewStatus())) {
                attendance.setReviewStatus("SUBMITTED_TO_HR");
                updateAttendance(attendance);
            }
        }
    }

    public List<Attendance> getAttendancesForUser(
            User user,
            Employee employee,
            Intern intern)
            throws ExecutionException, InterruptedException {

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        if (user != null) {
            addIdentity(ids, names, user.getId(), user.getFullName());
        }

        if (employee != null) {
            addIdentity(ids, names, employee.getId(), employee.getFullName());
        }

        if (intern != null) {
            addIdentity(ids, names, intern.getId(), intern.getFullName());
        }

        return getAllAttendances().stream()
                .filter(attendance -> matchesIdentity(attendance, ids, names))
                .toList();
    }

    public boolean isAttendanceForUser(
            Attendance attendance,
            User user,
            Employee employee,
            Intern intern) {

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        if (user != null) {
            addIdentity(ids, names, user.getId(), user.getFullName());
        }

        if (employee != null) {
            addIdentity(ids, names, employee.getId(), employee.getFullName());
        }

        if (intern != null) {
            addIdentity(ids, names, intern.getId(), intern.getFullName());
        }

        return matchesIdentity(attendance, ids, names);
    }

    public boolean isAttendanceForDepartment(
            Attendance attendance,
            List<Employee> employees,
            List<Intern> interns) {

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> departments = new HashSet<>();

        employees.forEach(employee -> {
            addIdentity(ids, names, employee.getId(), employee.getFullName());
            addDepartment(departments, employee.getDepartment());
        });
        interns.forEach(intern -> {
            addIdentity(ids, names, intern.getId(), intern.getFullName());
            addDepartment(departments, intern.getDepartment());
        });

        return matchesDepartment(attendance, departments) || matchesIdentity(attendance, ids, names);
    }

    // =========================
    // UPDATE
    // =========================

    public void updateAttendance(Attendance attendance)
            throws ExecutionException, InterruptedException {

        if (!hasText(attendance.getStatus())) {
            attendance.setStatus("PRESENT");
        }

        attendanceRepository.update(attendance);
    }

    // =========================
    // DELETE
    // =========================

    public void deleteAttendance(String id)
            throws ExecutionException, InterruptedException {

        attendanceRepository.deleteById(id);
    }

    private void addIdentity(
            Set<String> ids,
            Set<String> names,
            String id,
            String fullName) {

        if (id != null && !id.isBlank()) {
            ids.add(id.trim());
        }

        if (fullName != null && !fullName.isBlank()) {
            names.add(fullName.trim().toLowerCase());
        }
    }

    private void addDepartment(Set<String> departments, String department) {

        if (hasText(department)) {
            departments.add(department.trim().toLowerCase());
        }
    }

    private void assignAttendanceOwner(
            Attendance attendance,
            User user,
            Employee employee,
            Intern intern) {

        if (attendance == null) {
            throw new IllegalArgumentException("Invalid attendance information");
        }

        if (employee != null) {
            attendance.setEmployeeId(employee.getId());
            attendance.setEmployeeName(employee.getFullName());
            attendance.setDepartment(employee.getDepartment());
            return;
        }

        if (intern != null) {
            attendance.setEmployeeId(intern.getId());
            attendance.setEmployeeName(intern.getFullName());
            attendance.setDepartment(intern.getDepartment());
            return;
        }

        attendance.setEmployeeId(user.getId());
        attendance.setEmployeeName(user.getFullName());
        attendance.setDepartment(user.getDepartment());
    }

    private Attendance findAttendanceForDate(
            User user,
            Employee employee,
            Intern intern,
            String date)
            throws ExecutionException, InterruptedException {

        if (!hasText(date)) {
            return null;
        }

        return getAttendancesForUser(user, employee, intern).stream()
                .filter(attendance -> sameText(attendance.getDate(), date))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesIdentity(
            Attendance attendance,
            Set<String> ids,
            Set<String> names) {

        if (attendance == null) {
            return false;
        }

        String employeeId = attendance.getEmployeeId();
        String employeeName = attendance.getEmployeeName();

        return employeeId != null && ids.contains(employeeId.trim())
                || employeeName != null && names.contains(employeeName.trim().toLowerCase());
    }

    private boolean matchesDepartment(Attendance attendance, Set<String> departments) {

        return attendance != null
                && hasText(attendance.getDepartment())
                && departments.contains(attendance.getDepartment().trim().toLowerCase());
    }

    private String normalizeRole(String role) {

        if (role == null) {
            return "";
        }

        return role.replace("ROLE_", "").trim().toUpperCase();
    }

    private boolean hasText(String value) {

        return value != null && !value.trim().isEmpty();
    }

    private boolean sameText(String left, String right) {

        if (left == null || right == null) {
            return false;
        }

        return left.trim().equalsIgnoreCase(right.trim());
    }
}
