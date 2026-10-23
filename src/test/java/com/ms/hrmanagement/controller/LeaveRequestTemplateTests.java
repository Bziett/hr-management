package com.ms.hrmanagement.controller;

import com.ms.hrmanagement.model.LeaveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaveRequestTemplateTests {
    @Test
    void listAndDetailsShowReviewActionsOnlyForPendingRequestsAndReviewers() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servletContext = new MockServletContext();
        var request = new MockHttpServletRequest(servletContext);
        var response = new MockHttpServletResponse();
        var application = JakartaServletWebApplication.buildApplication(servletContext);
        var context = new WebContext(application.buildExchange(request, response));
        LeaveRequest leave = new LeaveRequest();
        leave.setId("leave-1");
        leave.setEmployeeName("Test Employee");
        context.setVariable("leaveRequest", leave);
        context.setVariable("leaveRequests", List.of(leave));
        context.setVariable("canCreateLeaveRequest", false);
        context.setVariable("manageableLeaveRequestIds", List.of("leave-1"));
        context.setVariable("canManageLeaveRequest", true);
        for (String template : List.of("leave-request/list", "leave-request/detail")) {
            for (String role : List.of("ADMIN", "HR", "MANAGER", "EMPLOYEE", "INTERN")) {
                context.setVariable("currentRole", role);
                context.setVariable("canReviewLeaveRequests", role.equals("ADMIN") || role.equals("HR"));
                for (String status : List.of("PENDING", "APPROVED", "REJECTED")) {
                    leave.setStatus(status);
                    String html = engine.process(template, context);
                    boolean canReview = status.equals("PENDING") && (role.equals("ADMIN") || role.equals("HR"));
                    assertEquals(canReview, html.contains(">Approve</button>"), template + " " + role + " " + status);
                    assertEquals(canReview, html.contains(">Reject</button>"), template + " " + role + " " + status);
                    assertTrue(html.contains(status));
                }
            }
        }
    }
}
