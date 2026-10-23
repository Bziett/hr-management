package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.*;
import com.ms.hrmanagement.repository.AttendanceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import java.time.*;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendanceServiceTests {
    private final AttendanceRepository repository = mock(AttendanceRepository.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T02:30:25Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final AttendanceService service = new AttendanceService(repository, clock);

    @Test
    void checkInUsesServerTimeAndCannotForgeOwnerOrSummary() throws Exception {
        Attendance input = row();
        input.setDate("2000-01-01"); input.setCheckIn("01:00"); input.setCheckOut("17:00");
        input.setStatus("REMOTE"); input.setReviewStatus("SUBMITTED_TO_HR"); input.setPhotoSubmitted(true);
        input.setManualEntry(true); input.setSummarySubmittedBy("forged");
        when(repository.findAll()).thenReturn(List.of());
        service.checkInForUser(input, user(), employee(), null);
        assertEquals("2026-10-23", input.getDate()); assertEquals("09:30:25", input.getCheckIn());
        assertEquals("EMPLOYEE-1", input.getEmployeeId()); assertEquals("Engineering", input.getDepartment());
        assertNull(input.getCheckOut()); assertFalse(input.isPhotoSubmitted()); assertFalse(input.isManualEntry());
        assertNull(input.getSummarySubmittedBy()); assertEquals("AWAITING_PHOTO", input.getReviewStatus());
        assertEquals("PRESENT", input.getStatus()); verify(repository).createDaily(input);
    }

    @Test
    void duplicateAndMissingCheckInAreRejected() throws Exception {
        when(repository.findAll()).thenReturn(List.of(row()));
        assertThrows(IllegalArgumentException.class, () -> service.checkInForUser(new Attendance(), user(), employee(), null));
        when(repository.findAll()).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.checkOutForUser(user(), employee(), null));
        verify(repository, never()).createDaily(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkOutPreservesManagerSubmissionAndUsesServerTime() throws Exception {
        Attendance row = row(); row.setPhotoSubmitted(true); row.setReviewStatus("SUBMITTED_TO_HR");
        when(repository.findAll()).thenReturn(List.of(row));
        doAnswer(call -> { ((Consumer<Attendance>) call.getArgument(1)).accept(row); return null; })
                .when(repository).change(eq("a1"), any());
        service.checkOutForUser(user(), employee(), null);
        assertEquals("09:30:25", row.getCheckOut()); assertEquals("SUBMITTED_TO_HR", row.getReviewStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkOutRequiresPhotoAndRejectsRepeat() throws Exception {
        Attendance row = row(); row.setReviewStatus("AWAITING_PHOTO");
        when(repository.findAll()).thenReturn(List.of(row));
        doAnswer(call -> { ((Consumer<Attendance>) call.getArgument(1)).accept(row); return null; })
                .when(repository).change(eq("a1"), any());
        assertThrows(IllegalArgumentException.class, () -> service.checkOutForUser(user(), employee(), null));
        row.setCheckOut("09:00");
        assertThrows(IllegalArgumentException.class, () -> service.checkOutForUser(user(), employee(), null));
    }

    @Test
    void hrOnlySeesSubmittedRecords() throws Exception {
        Attendance pending = row(); pending.setReviewStatus("SUBMITTED_TO_MANAGER");
        Attendance sent = row(); sent.setReviewStatus("SUBMITTED_TO_HR");
        when(repository.findAll()).thenReturn(List.of(pending, sent));
        assertEquals(List.of(sent), service.getAttendancesForHr());
    }

    @Test
    void sameNamesNeverGrantAccessToAnotherEmployeesRecords() {
        Attendance row = row(); row.setEmployeeId("OTHER"); row.setEmployeeName("Test Person");
        assertFalse(service.isAttendanceForUser(row, user(), employee(), null));
    }

    @Test
    void managerSubmitsOnlyTheirDepartmentAndSelectedDate() throws Exception {
        User manager = user(); manager.setRole("MANAGER");
        service.submitDepartmentAttendanceToHr(manager, "2026-10-22");
        verify(repository).submitDepartment(eq("Engineering"), eq("2026-10-22"), eq("test"), anyString());
        manager.setRole("EMPLOYEE");
        assertThrows(IllegalArgumentException.class, () -> service.submitDepartmentAttendanceToHr(manager, "2026-10-22"));
    }

    @Test
    void manualCheckInRequiresReasonAndIsAudited() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        Attendance input = row(); input.setCheckIn("08:00");
        assertThrows(IllegalArgumentException.class, () -> service.createManual(input, employee(), null, "hr"));
        input.setCorrectionReason("Forgot to check in");
        service.createManual(input, employee(), null, "hr");
        assertTrue(input.isManualEntry()); assertFalse(input.isPhotoSubmitted());
        assertEquals("hr", input.getCorrectedBy()); assertEquals("SUBMITTED_TO_HR", input.getReviewStatus());
    }

    @Test
    void manualEntryCannotUseFutureDateOrTime() {
        Attendance input = row(); input.setCorrectionReason("Forgot"); input.setDate("2026-10-24");
        assertThrows(IllegalArgumentException.class, () -> service.createManual(input, employee(), null, "hr"));
        input.setDate("2026-10-23"); input.setCheckIn("23:00");
        assertThrows(IllegalArgumentException.class, () -> service.createManual(input, employee(), null, "hr"));
    }

    @Test
    void missingCheckOutOnlyAppliesToPastDaysWithACheckInAndNoCheckOut() {
        Attendance input = row();
        assertFalse(service.isMissingCheckOut(input));
        input.setDate("2026-10-22");
        assertTrue(service.isMissingCheckOut(input));
        input.setCheckOut("  ");
        assertTrue(service.isMissingCheckOut(input));
        input.setCheckOut("17:00");
        assertFalse(service.isMissingCheckOut(input));
        input.setCheckOut(null); input.setCheckIn(null);
        assertFalse(service.isMissingCheckOut(input));
        input.setCheckIn("08:00"); input.setDate("2026-10-24");
        assertFalse(service.isMissingCheckOut(input));
        input.setDate("invalid");
        assertFalse(service.isMissingCheckOut(input));
        assertFalse(service.isMissingCheckOut(null));
    }

    private User user() { User u = new User(); u.setId("EMPLOYEE-1"); u.setRole("EMPLOYEE"); u.setUsername("test"); u.setDepartment("Engineering"); return u; }
    private Employee employee() { Employee e = new Employee(); e.setId("EMPLOYEE-1"); e.setFullName("Test Person"); e.setRole("EMPLOYEE"); e.setDepartment("Engineering"); return e; }
    private Attendance row() { Attendance a = new Attendance(); a.setId("a1"); a.setEmployeeId("EMPLOYEE-1"); a.setDepartment("Engineering"); a.setDate("2026-10-23"); a.setCheckIn("08:00"); return a; }
}
