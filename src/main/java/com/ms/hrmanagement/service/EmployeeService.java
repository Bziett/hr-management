package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.EmployeeRepository;
import com.ms.hrmanagement.repository.InternRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final InternRepository internRepository;
    private final StaffAccountService staffAccountService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            InternRepository internRepository,
            StaffAccountService staffAccountService) {

        this.employeeRepository = employeeRepository;
        this.internRepository = internRepository;
        this.staffAccountService = staffAccountService;
    }

    // =========================
    // CREATE
    // =========================

    public String createEmployee(Employee employee)
            throws ExecutionException, InterruptedException {

        validateAndNormalize(employee, null);
        return employeeRepository.save(employee);
    }

    // =========================
    // READ - BY ID
    // =========================

    public Employee getEmployeeById(String id)
            throws ExecutionException, InterruptedException {

        return employeeRepository.findById(id);
    }

    // =========================
    // READ - ALL
    // =========================

    public List<Employee> getAllEmployees()
            throws ExecutionException, InterruptedException {

        return employeeRepository.findAll();
    }

    public List<Employee> getEmployeesByDepartment(String department)
            throws ExecutionException, InterruptedException {

        return getAllEmployees().stream()
                .filter(employee -> sameText(employee.getDepartment(), department))
                .toList();
    }

    public long countEmployeesAndManagers(List<Employee> employees) {

        return employees.stream()
                .filter(employee -> isCountedEmployeeOrManager(employee.getRole()) || !hasText(employee.getRole()))
                .count();
    }

    public Employee getEmployeeByUser(User user)
            throws ExecutionException, InterruptedException {

        if (user == null) {
            return null;
        }

        String role = normalizeRole(user.getRole());

        if (!isDirectoryRole(role)) {
            return null;
        }

        Employee byEmail = employeeRepository.findByEmail(user.getEmail());

        if (byEmail != null) {
            return byEmail;
        }

        return getAllEmployees().stream()
                .filter(employee -> sameText(employee.getFullName(), user.getFullName()))
                .findFirst()
                .orElse(null);
    }

    public boolean belongsToDepartment(Employee employee, String department) {

        return employee != null && sameText(employee.getDepartment(), department);
    }

    public List<Employee> getSeniorEmployeesByDepartment(String department)
            throws ExecutionException, InterruptedException {

        return getAllEmployees().stream()
                .filter(employee -> !hasText(department) || sameText(employee.getDepartment(), department))
                .filter(employee -> "EMPLOYEE".equalsIgnoreCase(normalizeRole(employee.getRole())))
                .filter(employee -> "SENIOR".equalsIgnoreCase(employee.getPosition()))
                .toList();
    }

    // =========================
    // UPDATE
    // =========================

    public void updateEmployee(Employee employee)
            throws ExecutionException, InterruptedException {

        validateAndNormalize(employee, employee.getId());
        staffAccountService.update(employee);
    }

    // =========================
    // DELETE
    // =========================

    public void deleteEmployee(String id)
            throws ExecutionException, InterruptedException {

        employeeRepository.deleteById(id);
    }

    private void validateAndNormalize(Employee employee, String currentId)
            throws ExecutionException, InterruptedException {

        if (employee == null) {
            throw new IllegalArgumentException("Invalid employee information");
        }

        if (!hasText(employee.getFullName())) {
            throw new IllegalArgumentException("Full name is required");
        }

        if (!hasText(employee.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }

        if (!hasText(employee.getDepartment())) {
            throw new IllegalArgumentException("Please select a department");
        }

        Employee existingByEmail = employeeRepository.findByEmail(employee.getEmail().trim());

        if (existingByEmail != null && !sameText(existingByEmail.getId(), currentId)) {
            throw new IllegalArgumentException("Email is already used by another employee profile");
        }

        if (internRepository.findByEmail(employee.getEmail().trim()) != null) {
            throw new IllegalArgumentException("Email is already used by an intern profile");
        }

        String role = normalizeRole(employee.getRole());

        if (!isDirectoryRole(role)) {
            throw new IllegalArgumentException("Role must be HR, Manager, or Employee");
        }

        if (!hasText(employee.getEmployeeCode())) {
            employee.setEmployeeCode(generateEmployeeCode(role));
        }

        employee.setEmployeeCode(employee.getEmployeeCode().trim());
        employee.setFullName(employee.getFullName().trim());
        employee.setEmail(employee.getEmail().trim());
        employee.setDepartment(employee.getDepartment().trim());
        employee.setRole(role);
        employee.setPosition(normalizePosition(role, employee.getPosition()));

        if (!hasText(employee.getStatus())) {
            employee.setStatus("ACTIVE");
        }
    }

    private String normalizeRole(String role) {

        if (!hasText(role)) {
            return "";
        }

        return role.replace("ROLE_", "").trim().toUpperCase();
    }

    private String normalizePosition(String role, String position) {

        if ("HR".equals(role)) {
            return "HR";
        }

        if ("MANAGER".equals(role)) {
            return "";
        }

        if (!hasText(position)) {
            throw new IllegalArgumentException("Please select Senior or Junior for this employee");
        }

        if ("SENIOR".equalsIgnoreCase(position.trim())) {
            return "Senior";
        }

        if ("JUNIOR".equalsIgnoreCase(position.trim())) {
            return "Junior";
        }

        throw new IllegalArgumentException("Employee position must be Senior or Junior");
    }

    private boolean isDirectoryRole(String role) {

        return "HR".equalsIgnoreCase(role)
                || "MANAGER".equalsIgnoreCase(role)
                || "EMPLOYEE".equalsIgnoreCase(role);
    }

    private boolean isCountedEmployeeOrManager(String role) {

        return "MANAGER".equalsIgnoreCase(role) || "EMPLOYEE".equalsIgnoreCase(role);
    }

    private String generateEmployeeCode(String role) {

        String prefix = switch (normalizeRole(role)) {
            case "HR" -> "HR";
            case "MANAGER" -> "MGR";
            default -> "EMP";
        };
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        return prefix + "-" + suffix;
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
