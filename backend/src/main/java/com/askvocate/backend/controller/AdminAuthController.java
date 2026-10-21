package com.askvocate.backend.controller;

import com.askvocate.backend.service.AppSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Password login for the local admin review console. */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AppSessionService sessions;
    private final String adminPassword;

    public AdminAuthController(AppSessionService sessions,
            @Value("${verification.admin-password:}") String adminPassword) {
        this.sessions = sessions;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, String> body) {
        String provided = body == null ? null : body.get("password");
        if (adminPassword == null || adminPassword.isBlank() || provided == null
                || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                        adminPassword.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Invalid admin password."));
        }
        String token = sessions.issue("admin", "ADMIN");
        return ResponseEntity.ok(Map.of("success", true, "appToken", token, "role", "ADMIN"));
    }
}
