package com.ms.hrmanagement.service;

import com.ms.hrmanagement.model.Employee;
import com.ms.hrmanagement.model.User;
import com.ms.hrmanagement.repository.RoleIds;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StaffRulesTests {
    @Test
    void rejectsDuplicatePhonesDespiteFormatting() {
        Employee first = staff("EMPLOYEE-A", "EMPLOYEE", "Engineering", "+1 (555) 123-4567");
        Employee second = staff("EMPLOYEE-B", "EMPLOYEE", "Product", "15551234567");
        assertThrows(IllegalArgumentException.class,
                () -> StaffRules.validate(second, null, null, List.of(first), List.of()));
    }

    @Test
    void rejectsSecondManagerEvenWithDifferentPhoneAndDepartmentCase() {
        Employee manager = staff("MANAGER-A", "MANAGER", "Engineering", "111");
        Employee candidate = staff("MANAGER-B", "MANAGER", " engineering ", "222");
        assertThrows(IllegalArgumentException.class,
                () -> StaffRules.validate(candidate, null, null, List.of(manager), List.of()));
    }

    @Test
    void allowsManagerToKeepTheirOwnDepartmentAndPhone() {
        Employee manager = staff("MANAGER-A", "MANAGER", "Engineering", "111");
        User account = new User();
        account.setId("MANAGER-A");
        account.setRole("MANAGER");
        account.setDepartment("Engineering");
        account.setEmail(manager.getEmail());
        account.setPhone("111");
        assertDoesNotThrow(() -> StaffRules.validate(manager, manager.getId(), account.getId(),
                List.of(manager), List.of(account)));
    }

    @Test
    void permitsManagersInDifferentDepartmentsAndBlankPhones() {
        Employee manager = staff("MANAGER-A", "MANAGER", "Engineering", "");
        Employee candidate = staff("MANAGER-B", "MANAGER", "Product", "");
        assertDoesNotThrow(() -> StaffRules.validate(candidate, null, null, List.of(manager), List.of()));
    }

    @Test
    void promotionToManagerIsRejectedForOccupiedDepartment() {
        Employee manager = staff("MANAGER-A", "MANAGER", "Engineering", "111");
        Employee candidate = staff("EMPLOYEE-B", "MANAGER", "Engineering", "222");
        assertThrows(IllegalArgumentException.class,
                () -> StaffRules.validate(candidate, candidate.getId(), null, List.of(manager), List.of()));
    }

    @Test
    void alsoChecksAccountsWithoutProfiles() {
        User account = new User();
        account.setId("HR-A");
        account.setRole("HR");
        account.setPhone("555-123");
        Employee candidate = staff("EMPLOYEE-B", "EMPLOYEE", "Product", "555123");
        assertThrows(IllegalArgumentException.class,
                () -> StaffRules.validate(candidate, null, null, List.of(), List.of(account)));
    }

    @Test
    void roleIdsAreUniqueAndExistingMatchingIdsStayStable() {
        String id = RoleIds.newId("ROLE_HR");
        assertTrue(id.startsWith("HR-"));
        assertNotEquals(id, RoleIds.newId("HR"));
        assertEquals(id, RoleIds.forRole(id, "HR"));
        assertTrue(RoleIds.forRole(id, "MANAGER").startsWith("MANAGER-"));
        assertThrows(IllegalArgumentException.class, () -> RoleIds.newId("../users"));
    }

    private Employee staff(String id, String role, String department, String phone) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmail(id + "@example.test");
        employee.setRole(role);
        employee.setDepartment(department);
        employee.setPhone(phone);
        return employee;
    }
}
