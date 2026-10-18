package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.Employee;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
@SuppressWarnings("null")
public class EmployeeRepository {

    private final Firestore firestore;

    private static final String COLLECTION = "employees";

    public EmployeeRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Add employee
    public String save(Employee employee) throws ExecutionException, InterruptedException {

        DocumentReference document = firestore
                .collection(COLLECTION)
                .document();

        employee.setId(document.getId());

        document.set(employee).get();

        return employee.getId();
    }

    // Find employee by ID
    public Employee findById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Employee ID is required");

        DocumentSnapshot document = firestore
                .collection(COLLECTION)
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            return null;
        }

        return document.toObject(Employee.class);
    }

    public Employee findByEmail(String email)
            throws ExecutionException, InterruptedException {

        if (email == null || email.isBlank()) {
            return null;
        }

        QuerySnapshot snapshot = firestore
                .collection(COLLECTION)
                .whereEqualTo("email", email)
                .get()
                .get();

        if (snapshot.isEmpty()) {
            return null;
        }

        return snapshot.getDocuments()
                .get(0)
                .toObject(Employee.class);
    }

    // Get all employees
    public List<Employee> findAll()
            throws ExecutionException, InterruptedException {

        ApiFuture<QuerySnapshot> future = firestore
                .collection(COLLECTION)
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Employee> employees = new ArrayList<>();

        for (QueryDocumentSnapshot document : documents) {
            employees.add(document.toObject(Employee.class));
        }

        return employees;
    }

    // Update employee
    public void update(Employee employee)
            throws ExecutionException, InterruptedException {

        requireText(employee.getId(), "Employee ID is required");

        firestore
                .collection(COLLECTION)
                .document(employee.getId())
                .set(employee)
                .get();
    }

    // Delete employee
    public void deleteById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Employee ID is required");

        firestore
                .collection(COLLECTION)
                .document(id)
                .delete()
                .get();
    }

    private void requireText(String value, String message) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
