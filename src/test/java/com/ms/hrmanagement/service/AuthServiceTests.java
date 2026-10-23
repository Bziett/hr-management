package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.EmployeeRepository;
import com.ms.hrmanagement.repository.InternRepository;
import com.ms.hrmanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTests {
    private final UserRepository users = mock(UserRepository.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    private final EmployeeRepository employees = mock(EmployeeRepository.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    private final InternRepository interns = mock(InternRepository.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    private final StaffAccountService staff = mock(StaffAccountService.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthService auth = new AuthService(users, employees, interns, encoder, staff);

    @Test
    void createsHrWithPhoneAndHireDateInTheAtomicStaffOperation() throws Exception {
        User user = account();
        user.setAccountCode("caller-controlled-code");
        auth.createHrAccount(user);
        ArgumentCaptor<Employee> profile = ArgumentCaptor.forClass(Employee.class);
        verify(staff).create(same(user), profile.capture());
        assertEquals("HR", profile.getValue().getRole());
        assertEquals("555123456", profile.getValue().getPhone());
        assertEquals("2026-10-19", profile.getValue().getHireDate());
        assertTrue(user.getAccountCode().startsWith("HR-"));
        assertTrue(encoder.matches("12345678", user.getPassword()));
        verify(users, never()).save(any());
        verify(employees, never()).save(any());
    }

    @Test
    void managerIgnoresSubmittedSeniorPosition() throws Exception {
        User user = account();
        user.setRole("MANAGER");
        user.setPosition("Senior");
        auth.createManagerOrEmployeeAccount(user);
        ArgumentCaptor<Employee> profile = ArgumentCaptor.forClass(Employee.class);
        verify(staff).create(same(user), profile.capture());
        assertEquals("", user.getPosition());
        assertEquals("", profile.getValue().getPosition());
    }

    @Test
    void employeeMustChooseSeniorOrJuniorBeforeAnyWrites() {
        User user = account();
        user.setRole("EMPLOYEE");
        user.setPosition("Manager");
        assertThrows(IllegalArgumentException.class, () -> auth.createManagerOrEmployeeAccount(user));
        verifyNoInteractions(staff);
    }

    private User account() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("12345678");
        user.setFullName("Test User");
        user.setEmail("test@example.test");
        user.setDepartment("Engineering");
        user.setPhone("555123456");
        user.setHireDate("2026-10-19");
        return user;
    }
}
