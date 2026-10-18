package com.askvocate.backend.controller;

import com.askvocate.backend.service.AppSessionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAuthControllerTest {
    private final AppSessionService sessions = mock(AppSessionService.class);

    @Test
    void correctPasswordIssuesAdminToken() {
        when(sessions.issue("admin", "ADMIN")).thenReturn("admin-token");
        var controller = new AdminAuthController(sessions, "secret");

        var response = controller.login(Map.of("password", "secret"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("admin-token", response.getBody().get("appToken"));
        assertEquals("ADMIN", response.getBody().get("role"));
    }

    @Test
    void wrongPasswordIsRejected() {
        var controller = new AdminAuthController(sessions, "secret");

        var response = controller.login(Map.of("password", "wrong"));

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(sessions);
    }
}
