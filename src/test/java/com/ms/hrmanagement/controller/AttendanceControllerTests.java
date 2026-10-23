package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.*;
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

class AttendanceControllerTests {
    private final AttendanceService service = stub(AttendanceService.class);
    private final AuthService auth = stub(AuthService.class);
    private final EmployeeService employees = stub(EmployeeService.class);
    private final InternService interns = stub(InternService.class);
    private final AttendanceController controller = new AttendanceController(service, employees, interns, auth, stub(AttendancePhotoService.class));

    @Test
    void staffAndManagerCannotCreateSupplementaryRecords() {
        for (String role : List.of("MANAGER", "EMPLOYEE", "INTERN")) {
            assertThrows(AccessDeniedException.class, () -> controller.supplementForm(new ExtendedModelMap(), login(role)));
            assertThrows(AccessDeniedException.class, () -> controller.supplement("id", "EMPLOYEE", "2026-10-23", "08:00", "", "reason", login(role), new RedirectAttributesModelMap()));
        }
        verifyNoInteractions(service);
    }

    @Test
    void hrCannotReadUnsubmittedDetailsOrPhotoThroughDirectUrl() throws Exception {
        var row = new Attendance(); when(service.getAttendanceById("id")).thenReturn(row);
        when(service.isVisibleToHr(row)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> controller.photo("id", login("HR")));
        assertThrows(AccessDeniedException.class, () -> controller.detail("id", new ExtendedModelMap(), login("HR")));
        verify(service, never()).getPhoto(anyString());
    }

    @Test
    void managerCannotReadAnotherDepartmentsPhoto() throws Exception {
        var row = new Attendance(); row.setDepartment("Finance");
        var manager = new User(); manager.setDepartment("Engineering");
        var authentication = login("MANAGER"); when(auth.getAuthenticatedUser(authentication)).thenReturn(manager);
        when(service.getAttendanceById("id")).thenReturn(row);
        assertThrows(AccessDeniedException.class, () -> controller.photo("id", authentication));
        verify(service, never()).getPhoto(anyString());
    }

    @Test
    void hrListOnlyLoadsSubmittedRecordsAndFiltersByDepartmentAndDate() throws Exception {
        var row = new Attendance(); row.setDate("2026-10-23"); row.setDepartment("Engineering");
        var other = new Attendance(); other.setDate("2026-10-22"); other.setDepartment("Finance");
        when(service.getAttendancesForHr()).thenReturn(List.of(row, other));
        var model = new ExtendedModelMap();
        controller.list(model, login("HR"), "Engineering", "2026-10-23", "all");
        assertEquals(List.of(row), model.get("attendances"));
        verify(service, never()).getAllAttendances();
    }

    @Test
    void nonManagerCannotSubmitDepartmentSummary() {
        assertThrows(AccessDeniedException.class, () -> controller.submitDepartment(login("EMPLOYEE"), "2026-10-23", new RedirectAttributesModelMap()));
        verifyNoInteractions(service);
    }

    @Test
    void missingCheckOutViewIncludesPastDaysButPreservesDepartmentScope() throws Exception {
        var missing = new Attendance(); missing.setId("missing"); missing.setDate("2026-10-21"); missing.setDepartment("Engineering");
        var otherDepartment = new Attendance(); otherDepartment.setId("other"); otherDepartment.setDate("2026-10-20"); otherDepartment.setDepartment("Finance");
        var completed = new Attendance(); completed.setId("completed"); completed.setDate("2026-10-22"); completed.setDepartment("Engineering");
        when(service.getAttendancesForHr()).thenReturn(List.of(missing, otherDepartment, completed));
        when(service.isMissingCheckOut(missing)).thenReturn(true);
        when(service.isMissingCheckOut(otherDepartment)).thenReturn(true);
        var model = new ExtendedModelMap();
        controller.list(model, login("HR"), "Engineering", null, "missing-check-out");
        assertEquals(List.of(missing), model.get("attendances"));
        assertEquals(List.of("missing"), model.get("missingCheckOutIds"));
        assertEquals("", model.get("selectedDate"));
        controller.list(model, login("HR"), "Engineering", "2026-10-22", "missing-check-out");
        assertEquals(List.of(), model.get("attendances"));
    }

    private UsernamePasswordAuthenticationToken login(String role) {
        return new UsernamePasswordAuthenticationToken(role.toLowerCase(), "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private static <T> T stub(Class<T> type) { return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS)); }
}
