package com.ms.hrmanagement.repository;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.LeaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveRequestRepositoryTests {
    private final Firestore db = stub(Firestore.class);
    private final Transaction transaction = stub(Transaction.class);
    private final CollectionReference collection = stub(CollectionReference.class);
    private final DocumentReference document = stub(DocumentReference.class);
    private final DocumentSnapshot snapshot = stub(DocumentSnapshot.class);
    private final LeaveRequestRepository repository = new LeaveRequestRepository(db);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        when(db.collection("leave_requests")).thenReturn(collection);
        when(collection.document("leave-1")).thenReturn(document);
        when(transaction.get(document)).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("status")).thenReturn("PENDING");
        when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(call -> {
            Transaction.Function<Object> action = call.getArgument(0);
            try {
                return ApiFutures.immediateFuture(action.updateCallback(transaction));
            } catch (Exception exception) {
                return ApiFutures.immediateFailedFuture(exception);
            }
        });
    }

    @Test
    void approvesPendingRequestWithoutReplacingApplicantFields() throws Exception {
        repository.review("leave-1", "APPROVED", "admin");
        verify(transaction).update(document, Map.of("status", "APPROVED", "approvedBy", "admin"));
    }

    @Test
    void rejectsPendingRequestAndRecordsReviewer() throws Exception {
        repository.review("leave-1", "REJECTED", "hr1");
        verify(transaction).update(document, Map.of("status", "REJECTED", "approvedBy", "hr1"));
    }

    @Test
    void cannotOverwriteAnExistingDecision() throws Exception {
        for (String status : new String[]{"APPROVED", "REJECTED"}) {
            when(snapshot.getString("status")).thenReturn(status);
            assertThrows(IllegalStateException.class, () -> repository.review("leave-1", "APPROVED", "admin"));
        }
        verify(transaction, never()).update(any(DocumentReference.class), anyMap());
    }

    @Test
    void missingRequestIsNotRecreated() throws Exception {
        when(snapshot.exists()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> repository.review("leave-1", "APPROVED", "admin"));
        verify(transaction, never()).update(any(DocumentReference.class), anyMap());
    }

    @Test
    void editingCannotForgeApprovalOrApplicantIdentity() throws Exception {
        LeaveRequest request = new LeaveRequest();
        request.setId("leave-1");
        request.setLeaveType("ANNUAL");
        request.setStartDate("2026-11-01");
        request.setEndDate("2026-11-02");
        request.setReason("Family leave");
        request.setStatus("APPROVED");
        request.setApprovedBy("forged");
        request.setEmployeeId("someone-else");
        repository.update(request);
        verify(transaction).update(document, Map.of("leaveType", "ANNUAL", "startDate", "2026-11-01",
                "endDate", "2026-11-02", "reason", "Family leave"));
    }

    private static <T> T stub(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS));
    }
}
