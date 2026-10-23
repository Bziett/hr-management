package com.ms.hrmanagement.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.Transaction;
import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.RoleIds;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class StaffAccountService {
    private final Firestore firestore;

    public StaffAccountService(Firestore firestore) {
        this.firestore = firestore;
    }

    public String create(User user, Employee employee) throws ExecutionException, InterruptedException {
        String id = RoleIds.newId(user.getRole());
        user.setId(id);
        employee.setId(id);
        return execute(transaction -> {
            var employees = transaction.get(firestore.collection("employees")).get().toObjects(Employee.class);
            var users = transaction.get(firestore.collection("users")).get().toObjects(User.class);
            var interns = transaction.get(firestore.collection("interns")).get();
            StaffRules.validate(employee, null, null, employees, users);
            if (users.stream().anyMatch(existing -> sameText(existing.getUsername(), user.getUsername()))) {
                throw new IllegalArgumentException("Username already exists");
            }
            if (interns.getDocuments().stream().anyMatch(intern -> sameText(intern.getString("email"), user.getEmail()))) {
                throw new IllegalArgumentException("Email is already used by an intern profile");
            }
            transaction.create(firestore.collection("users").document(id), user);
            transaction.create(firestore.collection("employees").document(id), employee);
            return id;
        });
    }

    public void update(Employee employee) throws ExecutionException, InterruptedException {
        String previousId = employee.getId();
        String nextId = RoleIds.forRole(previousId, employee.getRole());
        execute(transaction -> {
            var existing = transaction.get(firestore.collection("employees").document(previousId)).get();
            if (!existing.exists()) {
                throw new IllegalArgumentException("Employee profile no longer exists");
            }
            var employees = transaction.get(firestore.collection("employees")).get().toObjects(Employee.class);
            var users = transaction.get(firestore.collection("users")).get().toObjects(User.class);
            var interns = transaction.get(firestore.collection("interns")).get();
            User account = users.stream()
                    .filter(user -> sameText(user.getEmail(), existing.getString("email")))
                    .findFirst().orElse(null);
            if (account != null && "ADMIN".equalsIgnoreCase(account.getRole())) {
                throw new IllegalArgumentException("Admin accounts cannot be edited as employee profiles");
            }
            if ("HR".equalsIgnoreCase(existing.getString("role")) != "HR".equalsIgnoreCase(employee.getRole())) {
                throw new IllegalArgumentException("HR roles must be managed through HR accounts");
            }
            StaffRules.validate(employee, previousId, account == null ? null : account.getId(), employees, users);
            if (interns.getDocuments().stream().anyMatch(intern -> sameText(intern.getString("email"), employee.getEmail()))) {
                throw new IllegalArgumentException("Email is already used by an intern profile");
            }
            Map<String, String> movedIds = new HashMap<>();
            if (!previousId.equals(nextId)) {
                movedIds.put(previousId, nextId);
            }
            String oldAccountId = account == null ? null : account.getId();
            String newAccountId = account == null ? null
                    : oldAccountId.equals(previousId) ? nextId : RoleIds.forRole(oldAccountId, employee.getRole());
            if (account != null && !oldAccountId.equals(newAccountId)) {
                movedIds.put(oldAccountId, newAccountId);
            }
            // Resolve historical references before any transaction writes.
            List<QueryDocumentSnapshot> references = new ArrayList<>();
            if (!movedIds.isEmpty()) {
                references.addAll(transaction.get(firestore.collection("attendance")).get().getDocuments());
                references.addAll(transaction.get(firestore.collection("leave_requests")).get().getDocuments());
            }
            long affected = references.stream().filter(doc -> movedIds.containsKey(doc.getString("employeeId"))).count();
            if (affected > 440) {
                throw new IllegalArgumentException("Too many historical records to change this role in one transaction");
            }
            employee.setEmployeeCode(existing.getString("employeeCode"));
            employee.setId(nextId);
            if (!previousId.equals(nextId)) {
                transaction.create(firestore.collection("employees").document(nextId), employee);
                transaction.delete(existing.getReference());
            } else {
                transaction.set(existing.getReference(), employee);
            }
            if (account != null) {
                account.setId(newAccountId);
                account.setFullName(employee.getFullName());
                account.setEmail(employee.getEmail());
                account.setPhone(employee.getPhone());
                account.setHireDate(employee.getHireDate());
                account.setRole(employee.getRole());
                account.setPosition(employee.getPosition());
                account.setDepartment(employee.getDepartment());
                account.setStatus(employee.getStatus());
                if (!oldAccountId.equals(newAccountId)) {
                    transaction.create(firestore.collection("users").document(newAccountId), account);
                    transaction.delete(firestore.collection("users").document(oldAccountId));
                } else {
                    transaction.set(firestore.collection("users").document(oldAccountId), account);
                }
            }
            for (var reference : references) {
                String replacement = movedIds.get(reference.getString("employeeId"));
                if (replacement != null) {
                    transaction.update(reference.getReference(), "employeeId", replacement);
                }
            }
            return null;
        });
        employee.setId(nextId);
    }

    private <T> T execute(Transaction.Function<T> action) throws ExecutionException, InterruptedException {
        try {
            return firestore.runTransaction(action).get();
        } catch (ExecutionException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof IllegalArgumentException validation) {
                    throw validation;
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
