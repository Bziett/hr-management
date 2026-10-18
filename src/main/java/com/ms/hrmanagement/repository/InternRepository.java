package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.Intern;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
@SuppressWarnings("null")
public class InternRepository {

    private final Firestore firestore;

    private static final String COLLECTION = "interns";

    public InternRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Add intern
    public String save(Intern intern)
            throws ExecutionException, InterruptedException {

        DocumentReference document = firestore
                .collection(COLLECTION)
                .document();

        intern.setId(document.getId());

        document.set(intern).get();

        return intern.getId();
    }

    // Find by ID
    public Intern findById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Intern ID is required");

        DocumentSnapshot document = firestore
                .collection(COLLECTION)
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            return null;
        }

        return document.toObject(Intern.class);
    }

    public Intern findByEmail(String email)
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
                .toObject(Intern.class);
    }

    // Get all interns
    public List<Intern> findAll()
            throws ExecutionException, InterruptedException {

        ApiFuture<QuerySnapshot> future = firestore
                .collection(COLLECTION)
                .get();

        List<Intern> interns = new ArrayList<>();

        for (QueryDocumentSnapshot document : future.get().getDocuments()) {
            interns.add(document.toObject(Intern.class));
        }

        return interns;
    }

    // Update
    public void update(Intern intern)
            throws ExecutionException, InterruptedException {

        requireText(intern.getId(), "Intern ID is required");

        firestore
                .collection(COLLECTION)
                .document(intern.getId())
                .set(intern)
                .get();
    }

    // Delete
    public void deleteById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Intern ID is required");

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
