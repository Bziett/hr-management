package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.EmployeeRepository;
import com.ms.hrmanagement.repository.InternRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class InternService {

    private final InternRepository internRepository;
    private final EmployeeRepository employeeRepository;

    public InternService(
            InternRepository internRepository,
            EmployeeRepository employeeRepository) {

        this.internRepository = internRepository;
        this.employeeRepository = employeeRepository;
    }

    // =========================
    // CREATE
    // =========================

    public String createIntern(Intern intern)
            throws ExecutionException, InterruptedException {

        intern.setInternCode(null);
        validateIntern(intern, null);
        return internRepository.save(intern);
    }

    // =========================
    // READ - BY ID
    // =========================

    public Intern getInternById(String id)
            throws ExecutionException, InterruptedException {

        return internRepository.findById(id);
    }

    // =========================
    // READ - ALL
    // =========================

    public List<Intern> getAllInterns()
            throws ExecutionException, InterruptedException {

        return internRepository.findAll();
    }

    public List<Intern> getInternsByDepartment(String department)
            throws ExecutionException, InterruptedException {

        return getAllInterns().stream()
                .filter(intern -> sameText(intern.getDepartment(), department))
                .toList();
    }

    public Intern getInternByUser(User user)
            throws ExecutionException, InterruptedException {

        if (user == null) {
            return null;
        }

        if (!"INTERN".equals(normalizeRole(user.getRole()))) {
            return null;
        }

        Intern byEmail = internRepository.findByEmail(user.getEmail());

        if (byEmail != null) {
            return byEmail;
        }

        return getAllInterns().stream()
                .filter(intern -> sameText(intern.getFullName(), user.getFullName()))
                .findFirst()
                .orElse(null);
    }

    public boolean belongsToDepartment(Intern intern, String department) {

        return intern != null && sameText(intern.getDepartment(), department);
    }

    // =========================
    // UPDATE
    // =========================

    public void updateIntern(Intern intern)
            throws ExecutionException, InterruptedException {

        validateIntern(intern, intern.getId());
        internRepository.update(intern);
    }

    // =========================
    // DELETE
    // =========================

    public void deleteIntern(String id)
            throws ExecutionException, InterruptedException {

        internRepository.deleteById(id);
    }

    private void validateIntern(Intern intern, String currentId)
            throws ExecutionException, InterruptedException {

        if (intern == null) {
            throw new IllegalArgumentException("Invalid intern information");
        }

        if (!hasText(intern.getFullName())) {
            throw new IllegalArgumentException("Full name is required");
        }

        if (!hasText(intern.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }

        if (!hasText(intern.getDepartment())) {
            throw new IllegalArgumentException("Please select a department");
        }

        if (!hasText(intern.getUniversity())) {
            throw new IllegalArgumentException("University is required");
        }

        if (!hasText(intern.getMajor())) {
            throw new IllegalArgumentException("Major is required");
        }

        Intern existingByEmail = internRepository.findByEmail(intern.getEmail().trim());

        if (existingByEmail != null && !sameText(existingByEmail.getId(), currentId)) {
            throw new IllegalArgumentException("Email is already used by another intern profile");
        }

        if (employeeRepository.findByEmail(intern.getEmail().trim()) != null) {
            throw new IllegalArgumentException("Email is already used by an employee profile");
        }

        if (hasText(intern.getMentor())
                && employeeRepository.findAll().stream()
                    .noneMatch(employee -> sameText(employee.getDepartment(), intern.getDepartment())
                            && "EMPLOYEE".equalsIgnoreCase(normalizeRole(employee.getRole()))
                            && "SENIOR".equalsIgnoreCase(employee.getPosition())
                            && sameText(employee.getFullName(), intern.getMentor()))) {

            throw new IllegalArgumentException("Mentor must be a Senior employee from the same department");
        }

        if (!hasText(intern.getInternCode())) {
            intern.setInternCode(generateInternCode());
        }

        intern.setInternCode(intern.getInternCode().trim());
        intern.setFullName(intern.getFullName().trim());
        intern.setEmail(intern.getEmail().trim());
        intern.setDepartment(intern.getDepartment().trim());
        intern.setUniversity(intern.getUniversity().trim());
        intern.setMajor(intern.getMajor().trim());

        if (hasText(intern.getMentor())) {
            intern.setMentor(intern.getMentor().trim());
        }

        if (!hasText(intern.getStatus())) {
            intern.setStatus("ACTIVE");
        }
    }

    private String generateInternCode() {

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        return "INT-" + suffix;
    }

    private boolean hasText(String value) {

        return value != null && !value.trim().isEmpty();
    }

    private String normalizeRole(String role) {

        if (!hasText(role)) {
            return "";
        }

        return role.replace("ROLE_", "").trim().toUpperCase();
    }

    private boolean sameText(String left, String right) {

        if (left == null || right == null) {
            return false;
        }

        return left.trim().equalsIgnoreCase(right.trim());
    }
}
