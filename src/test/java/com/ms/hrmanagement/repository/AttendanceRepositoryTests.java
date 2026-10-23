package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.Attendance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendanceRepositoryTests {
    private final Firestore db = stub(Firestore.class);
    private final Transaction transaction = stub(Transaction.class);
    private final CollectionReference collection = stub(CollectionReference.class);
    private final DocumentReference reference = stub(DocumentReference.class);
    private final DocumentSnapshot document = stub(DocumentSnapshot.class);
    private final Query query = stub(Query.class);
    private final QuerySnapshot snapshot = stub(QuerySnapshot.class);
    private final AttendanceRepository repository = new AttendanceRepository(db);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        when(db.collection("attendance")).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(reference);
        when(collection.whereEqualTo(anyString(), any())).thenReturn(query);
        when(transaction.get(query)).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(transaction.get(reference)).thenReturn(ApiFutures.immediateFuture(document));
        when(snapshot.getDocuments()).thenReturn(List.of());
        when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(call -> {
            Transaction.Function<Object> action = call.getArgument(0);
            try { return ApiFutures.immediateFuture(action.updateCallback(transaction)); }
            catch (Exception exception) { return ApiFutures.immediateFailedFuture(exception); }
        });
    }

    @Test
    void dailyCreationUsesDeterministicIdAndRejectsAnExistingRecord() throws Exception {
        Attendance a = new Attendance(); a.setEmployeeId("EMPLOYEE-1"); a.setDate("2026-10-23");
        String id = repository.createDaily(a);
        assertTrue(id.startsWith("ATT-")); verify(transaction).create(reference, a);
        when(document.exists()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> repository.createDaily(a));
        assertEquals(id, a.getId());
    }

    @Test
    void managerSummaryOnlyIncludesSelectedDateAndAlreadyConfirmedRowsStayUnchanged() throws Exception {
        var today = row("2026-10-23", "SUBMITTED_TO_MANAGER", true);
        var yesterday = row("2026-10-22", "SUBMITTED_TO_MANAGER", true);
        var sent = row("2026-10-23", "SUBMITTED_TO_HR", true);
        when(snapshot.getDocuments()).thenReturn(List.of(today, yesterday, sent));
        assertEquals(1, repository.submitDepartment("Engineering", "2026-10-23", "manager", "now"));
        verify(transaction).update(reference, Map.of("reviewStatus", "SUBMITTED_TO_HR", "summarySubmittedBy", "manager", "summarySubmittedAt", "now"));
        verify(collection).whereEqualTo("department", "Engineering");
    }

    @Test
    void incompletePhotosBlockTheWholeSummary() throws Exception {
        var missingPhoto = row("2026-10-23", "AWAITING_PHOTO", false);
        when(snapshot.getDocuments()).thenReturn(List.of(missingPhoto));
        assertThrows(IllegalArgumentException.class, () -> repository.submitDepartment("Engineering", "2026-10-23", "manager", "now"));
        verify(transaction, never()).update(any(DocumentReference.class), anyMap());
    }

    @Test
    void photoCannotBeAttachedToAnotherOwnerOrAnotherDay() throws Exception {
        Attendance a = new Attendance(); a.setEmployeeId("EMPLOYEE-1"); a.setDate("2026-10-23"); a.setReviewStatus("AWAITING_PHOTO");
        when(document.toObject(Attendance.class)).thenReturn(a);
        assertThrows(IllegalArgumentException.class, () -> repository.attachPhoto("id", "OTHER", "2026-10-23", new byte[]{1}, "now"));
        assertThrows(IllegalArgumentException.class, () -> repository.attachPhoto("id", "EMPLOYEE-1", "2026-10-24", new byte[]{1}, "now"));
        verify(transaction, never()).update(any(DocumentReference.class), anyMap());
    }

    private QueryDocumentSnapshot row(String date, String status, boolean photo) {
        var row = stub(QueryDocumentSnapshot.class);
        when(row.getString("date")).thenReturn(date); when(row.getString("reviewStatus")).thenReturn(status);
        when(row.getBoolean("photoSubmitted")).thenReturn(photo); when(row.getReference()).thenReturn(reference);
        return row;
    }

    @Test
    void attachingPhotoWritesEvidenceAndManagerQueueTogether() throws Exception {
        Attendance a = new Attendance(); a.setEmployeeId("EMPLOYEE-1"); a.setDate("2026-10-23"); a.setReviewStatus("AWAITING_PHOTO");
        when(document.toObject(Attendance.class)).thenReturn(a);
        var photos = stub(CollectionReference.class); var photoRef = stub(DocumentReference.class);
        when(db.collection("attendance_photos")).thenReturn(photos); when(photos.document("id")).thenReturn(photoRef);
        repository.attachPhoto("id", "EMPLOYEE-1", "2026-10-23", new byte[]{1, 2}, "now");
        verify(transaction).set(eq(photoRef), argThat(data -> data.get("image") instanceof Blob));
        verify(transaction).update(reference, Map.of("photoSubmitted", true, "photoSubmittedAt", "now", "reviewStatus", "SUBMITTED_TO_MANAGER"));
        a.setReviewStatus("SUBMITTED_TO_MANAGER");
        assertThrows(IllegalArgumentException.class, () -> repository.attachPhoto("id", "EMPLOYEE-1", "2026-10-23", new byte[]{3}, "later"));
    }
    private static <T> T stub(Class<T> type) { return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS)); }
}
