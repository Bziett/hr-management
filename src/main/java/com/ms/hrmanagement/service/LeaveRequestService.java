package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.LeaveRequest;
import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
    }

    // =========================
    // CREATE
    // =========================

    public String createLeaveRequest(LeaveRequest leaveRequest)
            throws ExecutionException, InterruptedException {

        if (leaveRequest.getStatus() == null || leaveRequest.getStatus().isBlank()) {
            leaveRequest.setStatus("PENDING");
        }

        if (leaveRequest.getCreatedAt() == null || leaveRequest.getCreatedAt().isBlank()) {
            leaveRequest.setCreatedAt(LocalDate.now().toString());
        }

        return leaveRequestRepository.save(leaveRequest);
    }

    public String createLeaveRequestForUser(
            LeaveRequest leaveRequest,
            User user,
            Employee employee,
            Intern intern)
            throws ExecutionException, InterruptedException {

        if (employee != null) {
            leaveRequest.setEmployeeId(employee.getId());
            leaveRequest.setEmployeeName(employee.getFullName());

        } else if (intern != null) {
            leaveRequest.setEmployeeId(intern.getId());
            leaveRequest.setEmployeeName(intern.getFullName());

        } else if (user != null) {
            leaveRequest.setEmployeeId(user.getId());
            leaveRequest.setEmployeeName(user.getFullName());
        }

        leaveRequest.setStatus("PENDING");
        leaveRequest.setApprovedBy("");

        return createLeaveRequest(leaveRequest);
    }

    // =========================
    // READ - BY ID
    // =========================

    public LeaveRequest getLeaveRequestById(String id)
            throws ExecutionException, InterruptedException {

        return leaveRequestRepository.findById(id);
    }

    // =========================
    // READ - ALL
    // =========================

    public List<LeaveRequest> getAllLeaveRequests()
            throws ExecutionException, InterruptedException {

        return leaveRequestRepository.findAll();
    }

    public List<LeaveRequest> getLeaveRequestsForDepartment(
            List<Employee> employees,
            List<Intern> interns)
            throws ExecutionException, InterruptedException {

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        employees.forEach(employee -> addIdentity(ids, names, employee.getId(), employee.getFullName()));
        interns.forEach(intern -> addIdentity(ids, names, intern.getId(), intern.getFullName()));

        return getAllLeaveRequests().stream()
                .filter(leaveRequest -> matchesIdentity(leaveRequest, ids, names))
                .toList();
    }

    public List<LeaveRequest> getLeaveRequestsForUser(
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

        return getAllLeaveRequests().stream()
                .filter(leaveRequest -> matchesIdentity(leaveRequest, ids, names))
                .toList();
    }

    public boolean isLeaveRequestForUser(
            LeaveRequest leaveRequest,
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

        return matchesIdentity(leaveRequest, ids, names);
    }

    public boolean isLeaveRequestForDepartment(
            LeaveRequest leaveRequest,
            List<Employee> employees,
            List<Intern> interns) {

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        employees.forEach(employee -> addIdentity(ids, names, employee.getId(), employee.getFullName()));
        interns.forEach(intern -> addIdentity(ids, names, intern.getId(), intern.getFullName()));

        return matchesIdentity(leaveRequest, ids, names);
    }

    // =========================
    // UPDATE
    // =========================

    public void updateLeaveRequest(LeaveRequest leaveRequest)
            throws ExecutionException, InterruptedException {

        leaveRequestRepository.update(leaveRequest);
    }

    // =========================
    // DELETE
    // =========================

    public void deleteLeaveRequest(String id)
            throws ExecutionException, InterruptedException {

        leaveRequestRepository.deleteById(id);
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

    private boolean matchesIdentity(
            LeaveRequest leaveRequest,
            Set<String> ids,
            Set<String> names) {

        if (leaveRequest == null) {
            return false;
        }

        String employeeId = leaveRequest.getEmployeeId();
        String employeeName = leaveRequest.getEmployeeName();

        return employeeId != null && ids.contains(employeeId.trim())
                || employeeName != null && names.contains(employeeName.trim().toLowerCase());
    }
}
