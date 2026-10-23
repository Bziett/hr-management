package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.Attendance;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
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

    public String createDaily(Attendance attendance) throws ExecutionException, InterruptedException {
        String key = attendance.getEmployeeId() + ":" + attendance.getDate();
        String id = "ATT-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        attendance.setId(id);
        return execute(transaction -> {
            var document = firestore.collection(COLLECTION).document(id);
            var existing = transaction.get(document).get();
            var legacy = transaction.get(firestore.collection(COLLECTION)
                    .whereEqualTo("employeeId", attendance.getEmployeeId())).get();
            if (existing.exists() || legacy.getDocuments().stream()
                    .anyMatch(row -> attendance.getDate().equals(row.getString("date")))) {
                throw new IllegalArgumentException("A check-in already exists for this person and date");
            }
            transaction.create(document, attendance);
            return id;
        });
    }

    public void change(String id, Consumer<Attendance> mutation) throws ExecutionException, InterruptedException {
        execute(transaction -> {
            var document = firestore.collection(COLLECTION).document(id);
            Attendance attendance = transaction.get(document).get().toObject(Attendance.class);
            if (attendance == null) throw new IllegalArgumentException("Attendance record no longer exists");
            attendance.setId(id);
            mutation.accept(attendance);
            transaction.set(document, attendance);
            return null;
        });
    }

    public void attachPhoto(String id, String ownerId, String date, byte[] image, String uploadedAt)
            throws ExecutionException, InterruptedException {
        execute(transaction -> {
            var document = firestore.collection(COLLECTION).document(id);
            Attendance attendance = transaction.get(document).get().toObject(Attendance.class);
            if (attendance == null || !ownerId.equals(attendance.getEmployeeId()) || !date.equals(attendance.getDate())) {
                throw new IllegalArgumentException("You can only submit your own photo for today");
            }
            if (!"AWAITING_PHOTO".equals(attendance.getReviewStatus())) {
                throw new IllegalArgumentException("A photo has already been submitted for this check-in");
            }
            transaction.set(firestore.collection("attendance_photos").document(id),
                    Map.of("image", Blob.fromBytes(image), "uploadedAt", uploadedAt));
            transaction.update(document, Map.of("photoSubmitted", true, "photoSubmittedAt", uploadedAt,
                    "reviewStatus", "SUBMITTED_TO_MANAGER"));
            return null;
        });
    }

    public byte[] findPhoto(String id) throws ExecutionException, InterruptedException {
        var document = firestore.collection("attendance_photos").document(id).get().get();
        Blob image = document.getBlob("image");
        return image == null ? null : image.toBytes();
    }

    public void correctTimes(String id, String checkIn, String checkOut, String reason, String reviewer, String at)
            throws ExecutionException, InterruptedException {
        String auditId = UUID.randomUUID().toString();
        execute(transaction -> {
            var document = firestore.collection(COLLECTION).document(id);
            Attendance row = transaction.get(document).get().toObject(Attendance.class);
            if (row == null) throw new IllegalArgumentException("Attendance no longer exists");
            transaction.create(document.collection("corrections").document(auditId), Map.of(
                    "previousCheckIn", row.getCheckIn() == null ? "" : row.getCheckIn(),
                    "previousCheckOut", row.getCheckOut() == null ? "" : row.getCheckOut(),
                    "checkIn", checkIn, "checkOut", checkOut, "reason", reason, "reviewer", reviewer, "at", at));
            transaction.update(document, Map.of("checkIn", checkIn, "checkOut", checkOut,
                    "correctionReason", reason, "correctedBy", reviewer, "correctedAt", at));
            return null;
        });
    }

    public int submitDepartment(String department, String date, String reviewer, String submittedAt)
            throws ExecutionException, InterruptedException {
        return execute(transaction -> {
            var snapshot = transaction.get(firestore.collection(COLLECTION)
                    .whereEqualTo("department", department)).get();
            var rows = snapshot.getDocuments().stream().filter(row -> date.equals(row.getString("date")))
                    .filter(row -> !List.of("SUBMITTED_TO_HR", "REVIEWED").contains(row.getString("reviewStatus") == null
                            ? "" : row.getString("reviewStatus"))).toList();
            if (rows.isEmpty()) throw new IllegalArgumentException("There are no new check-ins to submit for this date");
            if (rows.size() > 400) throw new IllegalArgumentException("This summary exceeds the supported 400 records");
            for (var row : rows) {
                if (!Boolean.TRUE.equals(row.getBoolean("photoSubmitted"))
                        || !"SUBMITTED_TO_MANAGER".equals(row.getString("reviewStatus"))) {
                    throw new IllegalArgumentException("Every check-in must have a photo before submitting this date");
                }
            }
            for (var row : rows) {
                transaction.update(row.getReference(), Map.of("reviewStatus", "SUBMITTED_TO_HR",
                        "summarySubmittedBy", reviewer, "summarySubmittedAt", submittedAt));
            }
            return rows.size();
        });
    }

    private <T> T execute(Transaction.Function<T> action) throws ExecutionException, InterruptedException {
        try {
            return firestore.runTransaction(action).get();
        } catch (ExecutionException exception) {
            Throwable cause = exception;
            while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
            if (cause instanceof IllegalArgumentException invalid) throw invalid;
            throw exception;
        }
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
        firestore.collection("attendance_photos").document(id).delete().get();
    }

    private void requireText(String value, String message) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
