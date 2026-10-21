package com.askvocate.backend.config;

import com.askvocate.backend.service.AppSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** Requires an ADMIN app session for every /api/admin/** endpoint except admin login. */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AppSessionService sessions;

    public AdminAuthInterceptor(AppSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/admin/login".equals(request.getRequestURI())) {
            return true;
        }
        try {
            sessions.requireAdmin(request.getHeader("Authorization"));
            return true;
        } catch (ResponseStatusException error) {
            response.setStatus(error.getStatusCode().value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String reason = error.getReason() == null ? "Admin login required." : error.getReason();
            response.getWriter().write("{\"success\":false,\"error\":\"" + reason + "\"}");
            return false;
        }
    }
}
