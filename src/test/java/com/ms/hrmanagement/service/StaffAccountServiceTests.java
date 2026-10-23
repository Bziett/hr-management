package com.ms.hrmanagement.service;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffAccountServiceTests {
    private final Firestore db = stub(Firestore.class);
    private final Transaction transaction = stub(Transaction.class);
    private final CollectionReference users = stub(CollectionReference.class);
    private final CollectionReference employees = stub(CollectionReference.class);
    private final CollectionReference interns = stub(CollectionReference.class);
    private final QuerySnapshot userSnapshot = stub(QuerySnapshot.class);
    private final QuerySnapshot employeeSnapshot = stub(QuerySnapshot.class);
    private final QuerySnapshot internSnapshot = stub(QuerySnapshot.class);
    private final DocumentReference userRef = stub(DocumentReference.class);
    private final DocumentReference employeeRef = stub(DocumentReference.class);
    private final StaffAccountService service = new StaffAccountService(db);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        when(db.collection("users")).thenReturn(users);
        when(db.collection("employees")).thenReturn(employees);
        when(db.collection("interns")).thenReturn(interns);
        when(transaction.get(users)).thenReturn(ApiFutures.immediateFuture(userSnapshot));
        when(transaction.get(employees)).thenReturn(ApiFutures.immediateFuture(employeeSnapshot));
        when(transaction.get(interns)).thenReturn(ApiFutures.immediateFuture(internSnapshot));
        when(userSnapshot.toObjects(User.class)).thenReturn(List.of());
        when(employeeSnapshot.toObjects(Employee.class)).thenReturn(List.of());
        when(internSnapshot.getDocuments()).thenReturn(List.of());
        when(users.document(anyString())).thenReturn(userRef);
        when(employees.document(anyString())).thenReturn(employeeRef);
        when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(call -> {
            Transaction.Function<String> action = call.getArgument(0);
            try {
                return ApiFutures.immediateFuture(action.updateCallback(transaction));
            } catch (Exception exception) {
                return ApiFutures.immediateFailedFuture(exception);
            }
        });
    }

    @Test
    void createsBothDocumentsWithTheSameRolePrefixedId() throws Exception {
        User user = account();
        Employee employee = employee();
        String id = service.create(user, employee);
        assertTrue(id.startsWith("MANAGER-"));
        assertEquals(id, user.getId());
        assertEquals(id, employee.getId());
        verify(db).runTransaction(any(Transaction.Function.class));
        verify(transaction).create(userRef, user);
        verify(transaction).create(employeeRef, employee);
    }

    @Test
    void duplicateManagerDoesNotCreateEitherDocument() throws Exception {
        Employee existing = employee();
        existing.setId("MANAGER-existing");
        existing.setEmail("other@example.test");
        existing.setPhone("999");
        when(employeeSnapshot.toObjects(Employee.class)).thenReturn(List.of(existing));
        assertThrows(IllegalArgumentException.class, () -> service.create(account(), employee()));
        verify(transaction, never()).create(any(DocumentReference.class), any(Object.class));
    }

    @Test
    void duplicatePhoneDoesNotLeaveAnAccountBehind() throws Exception {
        Employee existing = employee();
        existing.setId("EMPLOYEE-existing");
        existing.setRole("EMPLOYEE");
        existing.setEmail("other@example.test");
        existing.setPhone("555-123");
        when(employeeSnapshot.toObjects(Employee.class)).thenReturn(List.of(existing));
        assertThrows(IllegalArgumentException.class, () -> service.create(account(), employee()));
        verify(transaction, never()).create(any(DocumentReference.class), any(Object.class));
    }

    @Test
    void changingRoleMovesBothIdsAndPreservesAttendanceAndPassword() throws Exception {
        User user = account();
        user.setId("EMPLOYEE-old");
        user.setRole("EMPLOYEE");
        user.setPassword("existing-password-hash");
        Employee candidate = employee();
        candidate.setId(user.getId());
        var existing = stub(DocumentSnapshot.class);
        when(existing.exists()).thenReturn(true);
        when(existing.getString("role")).thenReturn("EMPLOYEE");
        when(existing.getString("email")).thenReturn(user.getEmail());
        when(existing.getString("employeeCode")).thenReturn("EMP-EXISTING");
        when(existing.getReference()).thenReturn(employeeRef);
        when(transaction.get(employeeRef)).thenReturn(ApiFutures.immediateFuture(existing));
        when(userSnapshot.toObjects(User.class)).thenReturn(List.of(user));
        var attendance = stub(CollectionReference.class);
        var leave = stub(CollectionReference.class);
        var attendanceSnapshot = stub(QuerySnapshot.class);
        var leaveSnapshot = stub(QuerySnapshot.class);
        var attendanceRecord = stub(QueryDocumentSnapshot.class);
        var attendanceRef = stub(DocumentReference.class);
        when(db.collection("attendance")).thenReturn(attendance);
        when(db.collection("leave_requests")).thenReturn(leave);
        when(transaction.get(attendance)).thenReturn(ApiFutures.immediateFuture(attendanceSnapshot));
        when(transaction.get(leave)).thenReturn(ApiFutures.immediateFuture(leaveSnapshot));
        when(attendanceSnapshot.getDocuments()).thenReturn(List.of(attendanceRecord));
        when(leaveSnapshot.getDocuments()).thenReturn(List.of());
        when(attendanceRecord.getString("employeeId")).thenReturn("EMPLOYEE-old");
        when(attendanceRecord.getReference()).thenReturn(attendanceRef);

        service.update(candidate);

        assertTrue(candidate.getId().startsWith("MANAGER-"));
        assertEquals(candidate.getId(), user.getId());
        assertEquals("existing-password-hash", user.getPassword());
        assertEquals("EMP-EXISTING", candidate.getEmployeeCode());
        verify(transaction).update(attendanceRef, "employeeId", candidate.getId());
        verify(transaction).create(employeeRef, candidate);
        verify(transaction).create(userRef, user);
        verify(transaction).delete(employeeRef);
        verify(transaction).delete(userRef);
    }

    private User account() {
        User user = new User();
        user.setRole("MANAGER");
        user.setEmail("manager@example.test");
        user.setUsername("manager");
        return user;
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setRole("MANAGER");
        employee.setEmail("manager@example.test");
        employee.setDepartment("Engineering");
        employee.setPhone("555123");
        return employee;
    }

    private static <T> T stub(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS));
    }
}
