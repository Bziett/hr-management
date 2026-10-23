package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.Intern;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.EmployeeRepository;
import com.ms.hrmanagement.repository.InternRepository;
import com.ms.hrmanagement.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class AuthService implements UserDetailsService {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "12345678";
    private static final String DEFAULT_DEPARTMENT = "Unassigned";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final InternRepository internRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffAccountService staffAccountService;

    public AuthService(
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            InternRepository internRepository,
            PasswordEncoder passwordEncoder,
            StaffAccountService staffAccountService) {

        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.internRepository = internRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffAccountService = staffAccountService;
    }

    public User login(String username, String password)
            throws ExecutionException, InterruptedException {

        User user = userRepository.findByUsername(username);

        if (user == null || user.getPassword() == null) {
            return null;
        }

        if (!passwordMatches(password, user.getPassword())) {
            return null;
        }

        return user;
    }

    public boolean usernameExists(String username)
            throws ExecutionException, InterruptedException {

        return userRepository.findByUsername(username) != null;
    }

    public boolean emailExists(String email)
            throws ExecutionException, InterruptedException {

        if (!hasText(email)) {
            return false;
        }

        String normalizedEmail = email.trim();

        return userRepository.findByEmail(normalizedEmail) != null
                || userRepository.findAll().stream()
                    .anyMatch(existingUser -> sameText(existingUser.getEmail(), normalizedEmail));
    }

    public String register(User user)
            throws ExecutionException, InterruptedException {

        return registerIntern(user);
    }

    public String registerIntern(User user)
            throws ExecutionException, InterruptedException {

        requireDepartment(user);
        requireInternEducation(user);
        ensureProfileEmailAvailable(user.getEmail());

        user.setRole("INTERN");
        user.setStatus("ACTIVE");

        String userId = createUser(user);
        createInternProfileIfMissing(user);

        return userId;
    }

    public String createHrAccount(User user)
            throws ExecutionException, InterruptedException {

        user.setRole("HR");
        user.setStatus("ACTIVE");

        if (!hasText(user.getDepartment())) {
            user.setDepartment("HR");
        }

        ensureProfileEmailAvailable(user.getEmail());

        user.setPosition("HR");
        return createStaffAccount(user);
    }

    public String createManagerOrEmployeeAccount(User user)
            throws ExecutionException, InterruptedException {

        String role = normalizeRole(user.getRole());

        if (!"MANAGER".equals(role) && !"EMPLOYEE".equals(role)) {
            throw new IllegalArgumentException("HR can only create Manager or Employee accounts");
        }

        if (!hasText(user.getDepartment())) {
            throw new IllegalArgumentException("Please select a department for this account");
        }

        user.setRole(role);
        user.setPosition(normalizeStaffPosition(role, user.getPosition()));
        user.setStatus(defaultText(user.getStatus(), "ACTIVE"));

        ensureProfileEmailAvailable(user.getEmail());
        return createStaffAccount(user);
    }

    public String createAdminIfMissing()
            throws ExecutionException, InterruptedException {

        User existingAdmin = userRepository.findByUsername(DEFAULT_ADMIN_USERNAME);

        if (existingAdmin != null) {
            return existingAdmin.getId();
        }

        User admin = new User();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPassword(DEFAULT_ADMIN_PASSWORD);
        admin.setFullName("System Administrator");
        admin.setEmail("admin@hrmanagement.local");
        admin.setRole("ADMIN");
        admin.setDepartment("System");
        admin.setStatus("ACTIVE");

        return createUser(admin);
    }

    public List<User> getAllUsers()
            throws ExecutionException, InterruptedException {

        return userRepository.findAll();
    }

    public List<User> getAdminHrManagerAccounts()
            throws ExecutionException, InterruptedException {

        return userRepository.findAll().stream()
                .filter(user -> {
                    String role = normalizeRole(user.getRole());
                    return "ADMIN".equals(role) || "HR".equals(role) || "MANAGER".equals(role);
                })
                .toList();
    }

    public List<User> getUsersVisibleToHr()
            throws ExecutionException, InterruptedException {

        return userRepository.findAll().stream()
                .filter(user -> !"ADMIN".equalsIgnoreCase(user.getRole()))
                .filter(user -> !"HR".equalsIgnoreCase(user.getRole()))
                .toList();
    }

    public User getUserById(String id)
            throws ExecutionException, InterruptedException {

        return userRepository.findById(id);
    }

    public User getUserByUsername(String username)
            throws ExecutionException, InterruptedException {

        return userRepository.findByUsername(username);
    }

    public void syncEmployeeProfilesForDirectoryAccounts()
            throws ExecutionException, InterruptedException {

        for (User user : userRepository.findAll()) {
            String role = normalizeRole(user.getRole());

            if ("HR".equals(role) || "MANAGER".equals(role) || "EMPLOYEE".equals(role)) {
                createEmployeeProfileIfMissing(user);
            }
        }
    }

    public void syncAccountFromEmployeeProfile(Employee employee, String previousEmail)
            throws ExecutionException, InterruptedException {

        if (employee == null) {
            return;
        }

        User account = null;

        if (hasText(previousEmail)) {
            account = userRepository.findByEmail(previousEmail.trim());
        }

        if (account == null && hasText(employee.getEmail())) {
            account = userRepository.findByEmail(employee.getEmail().trim());
        }

        if (account == null || "ADMIN".equals(normalizeRole(account.getRole()))) {
            return;
        }

        if (hasText(employee.getEmail())) {
            User accountWithEmail = userRepository.findByEmail(employee.getEmail().trim());

            if (accountWithEmail != null && !sameText(accountWithEmail.getId(), account.getId())) {
                throw new IllegalArgumentException("Email is already registered by another account");
            }
        }

        account.setFullName(employee.getFullName());
        account.setEmail(employee.getEmail());
        account.setRole(normalizeRole(employee.getRole()));
        account.setDepartment(employee.getDepartment());
        account.setPosition(employee.getPosition());
        account.setStatus(defaultText(employee.getStatus(), "ACTIVE"));

        userRepository.update(account);
    }

    public void deleteAccountByEmail(String email)
            throws ExecutionException, InterruptedException {

        if (!hasText(email)) {
            return;
        }

        User account = userRepository.findByEmail(email.trim());

        if (account == null || "ADMIN".equals(normalizeRole(account.getRole()))) {
            return;
        }

        userRepository.deleteById(account.getId());
    }

    public User getAuthenticatedUser(Authentication authentication)
            throws ExecutionException, InterruptedException {

        if (authentication == null) {
            return null;
        }

        return getUserByUsername(authentication.getName());
    }

    public void changePassword(
            User user,
            String currentPassword,
            String newPassword)
            throws ExecutionException, InterruptedException {

        if (user == null) {
            throw new IllegalArgumentException("Unable to identify the current account");
        }

        if (!hasText(currentPassword)) {
            throw new IllegalArgumentException("Current password is required");
        }

        if (!passwordMatches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.update(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        try {
            User user = userRepository.findByUsername(username);

            if (user == null) {
                throw new UsernameNotFoundException("Account not found: " + username);
            }

            String role = normalizeRole(user.getRole());

            if (role.isBlank()) {
                role = "INTERN";
            }

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(role.replace("ROLE_", ""))
                    .disabled("INACTIVE".equalsIgnoreCase(user.getStatus()))
                    .build();

        } catch (ExecutionException e) {
            throw new UsernameNotFoundException("Unable to load account information", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UsernameNotFoundException("Unable to load account information", e);
        }
    }

    private String createUser(User user)
            throws ExecutionException, InterruptedException {

        prepareUser(user);
        return userRepository.save(user);
    }

    private void prepareUser(User user)
            throws ExecutionException, InterruptedException {

        validateUser(user);

        if (usernameExists(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (emailExists(user.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        user.setUsername(user.getUsername().trim());
        user.setFullName(user.getFullName().trim());
        user.setEmail(user.getEmail().trim());
        user.setDepartment(defaultText(user.getDepartment(), DEFAULT_DEPARTMENT));
        String role = normalizeRole(user.getRole());
        user.setRole(role);

        user.setAccountCode(generateCode(accountCodePrefix(role), null));

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("ACTIVE");
        }

    }

    private String createStaffAccount(User user)
            throws ExecutionException, InterruptedException {

        user.setPhone(defaultText(user.getPhone(), ""));
        user.setHireDate(LocalDate.parse(defaultText(user.getHireDate(), LocalDate.now().toString())).toString());
        prepareUser(user);
        Employee employee = employeeProfile(user);
        return staffAccountService.create(user, employee);
    }

    private void createEmployeeProfileIfMissing(User user)
            throws ExecutionException, InterruptedException {

        if (!hasText(user.getEmail()) || employeeRepository.findByEmail(user.getEmail()) != null) {
            return;
        }

        employeeRepository.save(employeeProfile(user));
    }

    private Employee employeeProfile(User user) {
        String role = normalizeRole(user.getRole());
        Employee employee = new Employee();
        employee.setEmployeeCode(generateCode(employeeCodePrefix(role), user.getId()));
        employee.setFullName(user.getFullName());
        employee.setEmail(user.getEmail());
        employee.setDepartment(defaultText(user.getDepartment(), DEFAULT_DEPARTMENT));
        employee.setRole(role);
        employee.setPosition(normalizeStaffPosition(role, user.getPosition()));
        employee.setPhone(defaultText(user.getPhone(), ""));
        employee.setHireDate(defaultText(user.getHireDate(), LocalDate.now().toString()));
        employee.setStatus(defaultText(user.getStatus(), "ACTIVE"));
        return employee;
    }

    private void createInternProfileIfMissing(User user)
            throws ExecutionException, InterruptedException {

        if (!hasText(user.getEmail()) || internRepository.findByEmail(user.getEmail()) != null) {
            return;
        }

        Intern intern = new Intern();
        intern.setInternCode(generateCode("INT", user.getId()));
        intern.setFullName(user.getFullName());
        intern.setEmail(user.getEmail());
        intern.setDepartment(defaultText(user.getDepartment(), DEFAULT_DEPARTMENT));
        intern.setUniversity(defaultText(user.getUniversity(), ""));
        intern.setMajor(defaultText(user.getMajor(), ""));
        intern.setStartDate(LocalDate.now().toString());
        intern.setStatus("ACTIVE");

        internRepository.save(intern);
    }

    private void validateUser(User user) {

        if (user == null) {
            throw new IllegalArgumentException("Invalid account information");
        }

        if (!hasText(user.getUsername())) {
            throw new IllegalArgumentException("Username is required");
        }

        if (!hasText(user.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }

        validatePassword(user.getPassword());

        if (!hasText(user.getFullName())) {
            throw new IllegalArgumentException("Full name is required");
        }

        if (!hasText(user.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }
    }

    private void validatePassword(String password) {

        if (!hasText(password) || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
    }

    private void requireDepartment(User user) {

        if (user == null || !hasText(user.getDepartment())) {
            throw new IllegalArgumentException("Please select a department");
        }
    }

    private void requireInternEducation(User user) {

        if (user == null || !hasText(user.getUniversity())) {
            throw new IllegalArgumentException("University is required");
        }

        if (!hasText(user.getMajor())) {
            throw new IllegalArgumentException("Major is required");
        }
    }

    private void ensureProfileEmailAvailable(String email)
            throws ExecutionException, InterruptedException {

        if (!hasText(email)) {
            return;
        }

        String normalizedEmail = email.trim();

        boolean usedByEmployee = employeeRepository.findByEmail(normalizedEmail) != null
                || employeeRepository.findAll().stream()
                    .anyMatch(employee -> sameText(employee.getEmail(), normalizedEmail));

        boolean usedByIntern = internRepository.findByEmail(normalizedEmail) != null
                || internRepository.findAll().stream()
                    .anyMatch(intern -> sameText(intern.getEmail(), normalizedEmail));

        if (usedByEmployee || usedByIntern) {
            throw new IllegalArgumentException("Email is already used by another staff or intern profile");
        }
    }

    private String normalizeStaffPosition(String role, String position) {

        if ("MANAGER".equals(role)) {
            return "";
        }

        if ("HR".equals(role)) {
            return "HR";
        }

        if ("EMPLOYEE".equals(role)) {
            if (!hasText(position)) {
                throw new IllegalArgumentException("Please select Senior or Junior for this employee");
            }

            String normalizedPosition = position.trim();

            if ("SENIOR".equalsIgnoreCase(normalizedPosition)) {
                return "Senior";
            }

            if ("JUNIOR".equalsIgnoreCase(normalizedPosition)) {
                return "Junior";
            }

            throw new IllegalArgumentException("Employee position must be Senior or Junior");
        }

        return defaultText(position, "");
    }

    private String normalizeRole(String role) {

        if (role == null) {
            return "";
        }

        return role.replace("ROLE_", "").trim().toUpperCase();
    }

    private String defaultText(String value, String fallback) {

        return hasText(value) ? value.trim() : fallback;
    }

    private String generateCode(String prefix, String id) {

        String source = UUID.randomUUID().toString().replace("-", "");
        String suffix = source.substring(0, 8);

        return prefix + "-" + suffix.toUpperCase();
    }

    private String accountCodePrefix(String role) {

        return switch (role) {
            case "ADMIN" -> "ADM";
            case "HR" -> "HR";
            case "MANAGER" -> "MGR";
            case "EMPLOYEE" -> "EMP";
            case "INTERN" -> "INT";
            default -> "USR";
        };
    }

    private String employeeCodePrefix(String role) {

        return switch (role) {
            case "HR" -> "HR";
            case "MANAGER" -> "MGR";
            default -> "EMP";
        };
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

    private boolean passwordMatches(String rawPassword, String storedPassword) {

        if (storedPassword.startsWith("$2a$")
                || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$")) {

            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        return storedPassword.equals(rawPassword);
    }
}
