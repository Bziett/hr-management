package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.LeaveRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
@SuppressWarnings("null")
public class LeaveRequestRepository {

    private final Firestore firestore;

    private static final String COLLECTION = "leave_requests";

    public LeaveRequestRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // Create leave request
    public String save(LeaveRequest leaveRequest)
            throws ExecutionException, InterruptedException {

        DocumentReference document = firestore
                .collection(COLLECTION)
                .document();

        leaveRequest.setId(document.getId());

        document.set(leaveRequest).get();

        return leaveRequest.getId();
    }

    // Find leave request by ID
    public LeaveRequest findById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Leave request ID is required");

        DocumentSnapshot document = firestore
                .collection(COLLECTION)
                .document(id)
                .get()
                .get();

        if (!document.exists()) {
            return null;
        }

        return document.toObject(LeaveRequest.class);
    }

    // Get all leave requests
    public List<LeaveRequest> findAll()
            throws ExecutionException, InterruptedException {

        ApiFuture<QuerySnapshot> future = firestore
                .collection(COLLECTION)
                .get();

        List<LeaveRequest> requests = new ArrayList<>();

        for (QueryDocumentSnapshot document : future.get().getDocuments()) {
            requests.add(document.toObject(LeaveRequest.class));
        }

        return requests;
    }

    // Update request
    public void update(LeaveRequest leaveRequest)
            throws ExecutionException, InterruptedException {

        requireText(leaveRequest.getId(), "Leave request ID is required");

        updatePending(leaveRequest.getId(), Map.of(
                "leaveType", leaveRequest.getLeaveType(),
                "startDate", leaveRequest.getStartDate(),
                "endDate", leaveRequest.getEndDate(),
                "reason", leaveRequest.getReason()));
    }

    public void review(String id, String status, String reviewer)
            throws ExecutionException, InterruptedException {
        updatePending(id, Map.of("status", status, "approvedBy", reviewer));
    }

    private void updatePending(String id, Map<String, Object> changes)
            throws ExecutionException, InterruptedException {
        requireText(id, "Leave request ID is required");
        DocumentReference document = firestore.collection(COLLECTION).document(id);
        try {
            // Read and write atomically so two reviewers cannot overwrite a decision.
            firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(document).get();
                if (!snapshot.exists()) {
                    throw new IllegalStateException("Leave request no longer exists");
                }
                if (!"PENDING".equals(snapshot.getString("status"))) {
                    throw new IllegalStateException("This leave request has already been processed");
                }
                transaction.update(document, changes);
                return null;
            }).get();
        } catch (ExecutionException exception) {
            Throwable cause = exception;
            while (cause.getCause() != null && cause != cause.getCause()) {
                cause = cause.getCause();
            }
            if (cause instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw exception;
        }
    }

    // Delete request
    public void deleteById(String id)
            throws ExecutionException, InterruptedException {

        requireText(id, "Leave request ID is required");

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
