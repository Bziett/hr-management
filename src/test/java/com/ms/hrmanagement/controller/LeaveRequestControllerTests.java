package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveRequestControllerTests {
    private final LeaveRequestService service = stub(LeaveRequestService.class);
    private final LeaveRequestController controller = new LeaveRequestController(service,
            stub(EmployeeService.class), stub(InternService.class), stub(AuthService.class));

    @Test
    void adminAndHrCanProcessRequestsWithTheirOwnUsername() throws Exception {
        for (String role : List.of("ADMIN", "HR")) {
            var auth = authentication(role);
            var flash = new RedirectAttributesModelMap();
            assertEquals("redirect:/leave-requests", controller.reviewLeaveRequest("leave-1", "APPROVED", auth, flash));
            verify(service).reviewLeaveRequest("leave-1", "APPROVED", role.toLowerCase());
            assertEquals("Leave request approved", flash.getFlashAttributes().get("successMessage"));
        }
    }

    @Test
    void otherRolesCannotReviewEvenWhenPostingDirectly() {
        for (String role : List.of("MANAGER", "EMPLOYEE", "INTERN")) {
            assertThrows(AccessDeniedException.class, () -> controller.reviewLeaveRequest(
                    "leave-1", "APPROVED", authentication(role), new RedirectAttributesModelMap()));
        }
        assertThrows(AccessDeniedException.class, () -> controller.reviewLeaveRequest(
                "leave-1", "APPROVED", null, new RedirectAttributesModelMap()));
        verifyNoInteractions(service);
    }

    @Test
    void staleDecisionShowsErrorInsteadOfSuccess() throws Exception {
        doThrow(new IllegalStateException("Already processed")).when(service)
                .reviewLeaveRequest("leave-1", "REJECTED", "hr");
        var flash = new RedirectAttributesModelMap();
        controller.reviewLeaveRequest("leave-1", "REJECTED", authentication("HR"), flash);
        assertEquals("Already processed", flash.getFlashAttributes().get("errorMessage"));
        assertFalse(flash.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void reviewControlsAreOnlyExposedToAdminAndHr() throws Exception {
        when(service.getAllLeaveRequests()).thenReturn(List.of());
        for (String role : List.of("ADMIN", "HR", "EMPLOYEE")) {
            var model = new ExtendedModelMap();
            controller.getAllLeaveRequests(model, authentication(role));
            assertEquals(!role.equals("EMPLOYEE"), model.get("canReviewLeaveRequests"));
        }
    }

    private UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken(role.toLowerCase(), "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static <T> T stub(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS));
    }
}
