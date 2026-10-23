package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.LeaveRequest;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveRequestServiceTests {
    private final LeaveRequestRepository repository = mock(LeaveRequestRepository.class,
            withSettings().mockMaker(MockMakers.SUBCLASS));
    private final LeaveRequestService service = new LeaveRequestService(repository);

    @Test
    void onlyFinalDecisionsAndAuthenticatedReviewersAreAccepted() {
        assertThrows(IllegalArgumentException.class, () -> service.reviewLeaveRequest("id", "PENDING", "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewLeaveRequest("id", "INVALID", "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewLeaveRequest("id", "APPROVED", " "));
        verifyNoInteractions(repository);
    }

    @Test
    void delegatesBothDecisions() throws Exception {
        service.reviewLeaveRequest("id", "APPROVED", "admin");
        service.reviewLeaveRequest("id", "REJECTED", "hr");
        verify(repository).review("id", "APPROVED", "admin");
        verify(repository).review("id", "REJECTED", "hr");
    }

    @Test
    void submittedRequestAlwaysStartsPendingWithoutAReviewer() throws Exception {
        LeaveRequest request = new LeaveRequest();
        request.setStatus("APPROVED");
        request.setApprovedBy("forged");
        User user = new User();
        user.setId("EMPLOYEE-1");
        user.setFullName("Test Employee");
        service.createLeaveRequestForUser(request, user, null, null);
        assertEquals("PENDING", request.getStatus());
        assertEquals("", request.getApprovedBy());
        assertEquals(user.getId(), request.getEmployeeId());
        verify(repository).save(request);
    }
}
