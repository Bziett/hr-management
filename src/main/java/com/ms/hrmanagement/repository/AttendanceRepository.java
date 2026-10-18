package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.Attendance;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Repository
@SuppressWarnings("null")
public class AttendanceRepository {

    private final Firestore firestore;

    private static final String COLLECTION = "attendance";

    public AttendanceRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Add attendance record
    public String save(Attendance attendance)
            throws ExecutionException, InterruptedException {

        DocumentReference document = firestore
                .collection(COLLECTION)
                .document();

        attendance.setId(document.getId());

        document.set(attendance).get();

        return attendance.getId();
    }

    // Find by ID
    public Attendance findById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Attendance ID is required");

        DocumentSnapshot document = firestore
                .collection(COLLECTION)
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            return null;
        }

        return document.toObject(Attendance.class);
    }

    // Get all records
    public List<Attendance> findAll()
            throws ExecutionException, InterruptedException {

        ApiFuture<QuerySnapshot> future = firestore
                .collection(COLLECTION)
                .get();

        List<Attendance> attendances = new ArrayList<>();

        for (QueryDocumentSnapshot document : future.get().getDocuments()) {
            attendances.add(document.toObject(Attendance.class));
        }

        return attendances;
    }

    // Update
    public void update(Attendance attendance)
            throws ExecutionException, InterruptedException {

        requireText(attendance.getId(), "Attendance ID is required");

        firestore
                .collection(COLLECTION)
                .document(attendance.getId())
                .set(attendance)
                .get();
    }

    // Delete
    public void deleteById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Attendance ID is required");

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
