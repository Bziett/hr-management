package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.Attendance;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AttendanceTemplateTests {
    @Test
    void formsRenderForEachRoleWithoutEditableSelfServiceTimes() {
        var resolver = new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var context = new WebContext(JakartaServletWebApplication.buildApplication(servlet)
                .buildExchange(new MockHttpServletRequest(servlet), new MockHttpServletResponse()));
        var record = new Attendance(); record.setId("a1"); record.setDate("2026-10-23"); record.setEmployeeName("Test Person"); record.setDepartment("Engineering");
        record.setReviewStatus("AWAITING_PHOTO");
        context.setVariable("attendance", record); context.setVariable("attendances", List.of(record));
        context.setVariable("departments", List.of("Engineering")); context.setVariable("selectedDepartment", "Engineering");
        context.setVariable("selectedDate", "2026-10-23"); context.setVariable("today", "2026-10-23");
        context.setVariable("missingCheckIns", List.of("Missing Person"));
        context.setVariable("missingCheckOutIds", List.of("a1"));
        context.setVariable("selectedView", "all");
        context.setVariable("employees", List.of()); context.setVariable("interns", List.of());
        for (String role : List.of("ADMIN", "HR", "MANAGER", "EMPLOYEE", "INTERN")) {
            boolean office = role.equals("ADMIN") || role.equals("HR");
            context.setVariable("currentRole", role); context.setVariable("canManageAttendance", office);
            context.setVariable("canCreateAttendance", !office); context.setVariable("canUploadPhoto", !office);
            String list = engine.process("attendance/list", context);
            assertTrue(list.contains("<span class=\"badge\">Missing Check-out</span>"));
            assertEquals(office, list.contains("Add Missing Check-in"));
            assertEquals(role.equals("MANAGER"), list.contains("Confirm and Submit to HR"));
            String detail = engine.process("attendance/detail", context);
            assertEquals(!office, detail.contains("Submit Photo"));
        }
        String add = engine.process("attendance/add", context);
        assertFalse(add.contains("name=\"checkIn\"")); assertFalse(add.contains("name=\"date\""));
        assertTrue(engine.process("attendance/supplement", context).contains("Save Missing Check-in"));
        assertTrue(engine.process("attendance/edit", context).contains("Correction Reason"));
        context.setVariable("currentRole", "MANAGER");
        context.setVariable("selectedView", "missing-check-out");
        assertFalse(engine.process("attendance/list", context).contains("Confirm and Submit to HR"));
    }
}
