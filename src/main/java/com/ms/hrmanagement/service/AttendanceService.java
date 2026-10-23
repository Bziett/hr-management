package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Attendance;
import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendanceRepository, Clock attendanceClock) {
        this.attendanceRepository = attendanceRepository;
        this.clock = attendanceClock;
    }

    public String today() { return LocalDate.now(clock).toString(); }
    private String now() { return LocalDateTime.now(clock).toString(); }

    public boolean isMissingCheckOut(Attendance attendance) {
        if (attendance == null || !hasText(attendance.getCheckIn()) || hasText(attendance.getCheckOut())
                || !hasText(attendance.getDate())) return false;
        try {
            return LocalDate.parse(attendance.getDate()).isBefore(LocalDate.now(clock));
        } catch (java.time.DateTimeException exception) {
            return false;
        }
    }

    public boolean isVisibleToHr(Attendance attendance) {
        return "SUBMITTED_TO_HR".equals(attendance.getReviewStatus()) || "REVIEWED".equals(attendance.getReviewStatus());
    }

    public List<Attendance> getAttendancesForHr() throws ExecutionException, InterruptedException {
        return getAllAttendances().stream().filter(this::isVisibleToHr).toList();
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

        return attendanceRepository.createDaily(attendance);
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
        if (!hasText(attendance.getDepartment())) {
            throw new IllegalArgumentException("A department must be assigned before checking in");
        }

        LocalDateTime arrivedAt = LocalDateTime.now(clock);
        attendance.setDate(arrivedAt.toLocalDate().toString());

        Attendance existingAttendance = findAttendanceForDate(user, employee, intern, attendance.getDate());

        if (existingAttendance != null) {
            throw new IllegalArgumentException("You have already checked in for this date");
        }

        attendance.setCheckIn(arrivedAt.toLocalTime().withNano(0).toString());
        attendance.setCheckOut(null);
        attendance.setPhotoSubmitted(false);
        attendance.setPhotoSubmittedAt(null);
        attendance.setSummarySubmittedBy(null);
        attendance.setSummarySubmittedAt(null);
        attendance.setManualEntry(false);
        attendance.setCorrectedBy(null);
        attendance.setCorrectedAt(null);
        attendance.setCorrectionReason(null);

        String role = normalizeRole(user.getRole());
        attendance.setSubmittedByRole(role);
        attendance.setReviewStatus("AWAITING_PHOTO");

        attendance.setStatus("PRESENT");

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

        LocalDateTime leavingAt = LocalDateTime.now(clock);
        Attendance attendance = findAttendanceForDate(user, employee, intern, leavingAt.toLocalDate().toString());

        if (attendance == null) {
            throw new IllegalArgumentException("Please check in before checking out");
        }

        if (!hasText(attendance.getCheckIn())) {
            throw new IllegalArgumentException("Please check in before checking out");
        }

        if (hasText(attendance.getCheckOut())) {
            throw new IllegalArgumentException("You have already checked out today");
        }

        String owner = attendance.getEmployeeId();
        String date = leavingAt.toLocalDate().toString();
        String time = leavingAt.toLocalTime().withNano(0).toString();
        attendanceRepository.change(attendance.getId(), current -> {
            if (!owner.equals(current.getEmployeeId()) || !date.equals(current.getDate())) {
                throw new IllegalArgumentException("Attendance ownership has changed");
            }
            if (!hasText(current.getCheckIn()) || hasText(current.getCheckOut())) {
                throw new IllegalArgumentException("Check-out is not available for this record");
            }
            if (!current.isPhotoSubmitted() && !current.isManualEntry()
                    && ("AWAITING_PHOTO".equals(current.getReviewStatus()) || "SUBMITTED_TO_MANAGER".equals(current.getReviewStatus()))) {
                throw new IllegalArgumentException("Submit today's check-in photo before checking out");
            }
            if (LocalTime.parse(time).isBefore(LocalTime.parse(current.getCheckIn()))) {
                throw new IllegalArgumentException("Check-out must be after check-in");
            }
            current.setCheckOut(time);
            // Preserve the Manager's submission when a later check-out arrives.
        });
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
                .filter(attendance -> matchesDepartment(attendance, departments))
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

    public int submitDepartmentAttendanceToHr(User manager, String date)
            throws ExecutionException, InterruptedException {
        if (manager == null || !"MANAGER".equals(normalizeRole(manager.getRole())) || !hasText(manager.getDepartment())) {
            throw new IllegalArgumentException("A Manager with an assigned department is required");
        }
        LocalDate selected = LocalDate.parse(date);
        if (selected.isAfter(LocalDate.now(clock))) throw new IllegalArgumentException("A future date cannot be submitted");
        return attendanceRepository.submitDepartment(manager.getDepartment(), date, manager.getUsername(), now());
    }

    public void submitPhoto(String id, User user, Employee employee, Intern intern, byte[] image)
            throws ExecutionException, InterruptedException {
        Attendance record = getAttendanceById(id);
        if (record == null || !isAttendanceForUser(record, user, employee, intern)) {
            throw new IllegalArgumentException("This check-in does not belong to you");
        }
        attendanceRepository.attachPhoto(id, record.getEmployeeId(), today(), image, now());
    }

    public byte[] getPhoto(String id) throws ExecutionException, InterruptedException {
        return attendanceRepository.findPhoto(id);
    }

    public void correctTimes(String id, String checkIn, String checkOut, String reason, String reviewer)
            throws ExecutionException, InterruptedException {
        if (!hasText(reason)) throw new IllegalArgumentException("A correction reason is required");
        LocalTime arrival = LocalTime.parse(checkIn);
        if (hasText(checkOut) && LocalTime.parse(checkOut).isBefore(arrival)) {
            throw new IllegalArgumentException("Check-out must be after check-in");
        }
        Attendance row = getAttendanceById(id);
        if (row == null) throw new IllegalArgumentException("Attendance no longer exists");
        if (today().equals(row.getDate()) && (arrival.isAfter(LocalTime.now(clock))
                || hasText(checkOut) && LocalTime.parse(checkOut).isAfter(LocalTime.now(clock)))) {
            throw new IllegalArgumentException("Attendance time cannot be in the future");
        }
        attendanceRepository.correctTimes(id, checkIn, checkOut == null ? "" : checkOut, reason, reviewer, now());
    }

    public String createManual(Attendance input, Employee employee, Intern intern, String reviewer)
            throws ExecutionException, InterruptedException {
        if (employee == null && intern == null) throw new IllegalArgumentException("Choose a staff member");
        LocalDate date = LocalDate.parse(input.getDate());
        if (date.isAfter(LocalDate.now(clock))) throw new IllegalArgumentException("Future check-ins are not allowed");
        LocalTime time = LocalTime.parse(input.getCheckIn());
        if (date.equals(LocalDate.now(clock)) && time.isAfter(LocalTime.now(clock))) {
            throw new IllegalArgumentException("Check-in time cannot be in the future");
        }
        if (!hasText(input.getCorrectionReason())) throw new IllegalArgumentException("A reason is required for manual check-in");
        if (hasText(input.getCheckOut()) && LocalTime.parse(input.getCheckOut()).isBefore(time)) {
            throw new IllegalArgumentException("Check-out must be after check-in");
        }
        if (date.equals(LocalDate.now(clock)) && hasText(input.getCheckOut())
                && LocalTime.parse(input.getCheckOut()).isAfter(LocalTime.now(clock))) {
            throw new IllegalArgumentException("Check-out time cannot be in the future");
        }
        User owner = new User();
        owner.setRole(employee != null ? employee.getRole() : "INTERN");
        assignAttendanceOwner(input, owner, employee, intern);
        if (findAttendanceForDate(owner, employee, intern, input.getDate()) != null) {
            throw new IllegalArgumentException("A check-in already exists for this person and date");
        }
        input.setStatus("PRESENT");
        input.setSubmittedByRole(normalizeRole(owner.getRole()));
        input.setReviewStatus("SUBMITTED_TO_HR");
        input.setManualEntry(true);
        input.setPhotoSubmitted(false);
        input.setCorrectedBy(reviewer);
        input.setCorrectedAt(now());
        return createAttendance(input);
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

        return matchesDepartment(attendance, departments);
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

        return employeeId != null && ids.contains(employeeId.trim());
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
