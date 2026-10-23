package com.ms.hrmanagement.controller;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AccountPasswordVisibilityTests {
    @Test
    void accountCreationPasswordsAreVisibleButLoginAndPasswordChangesStayMasked() throws Exception {
        for (String template : new String[]{"admin/accounts", "hr/accounts", "employee/add", "register"}) {
            String html = template(template);
            assertFalse(html.contains("type=\"password\""), template);
            assertTrue(html.contains("autocomplete=\"new-password\""), template);
            assertTrue(html.contains("minlength=\"8\""), template);
        }
        assertTrue(template("login").contains("type=\"password\""));
        for (String role : new String[]{"hr", "manager", "employee", "intern"}) {
            assertTrue(template(role + "/dashboard").contains("type=\"password\" name=\"currentPassword\""));
            assertTrue(template(role + "/dashboard").contains("type=\"password\" name=\"newPassword\""));
        }
    }

    private String template(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/templates/" + name + ".html")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
