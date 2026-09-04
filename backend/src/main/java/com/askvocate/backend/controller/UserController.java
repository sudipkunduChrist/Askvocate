package com.askvocate.backend.controller;

import com.askvocate.backend.dto.ClientSignUp;
import com.askvocate.backend.dto.LawyerExperiencedSignup;
import com.askvocate.backend.dto.LawyerFresherSignup;
import com.askvocate.backend.service.ClientService;
import com.askvocate.backend.service.LawyerExperiencedService;
import com.askvocate.backend.service.LawyerFresherService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles user registration and profile retrieval for all 3 roles.
 * Base path: /api/users
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private ClientService clientService;

    @Autowired
    private LawyerFresherService lawyerFresherService;

    @Autowired
    private LawyerExperiencedService lawyerExperiencedService;

    @Autowired
    private com.askvocate.backend.service.AuthService authService;

    // ─── Authentication ───────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody com.askvocate.backend.dto.LoginRequest dto) {
        log.info("--> Endpoint Hit: POST /api/users/login | Identifier: {}", dto.getEmailOrPhone());
        var data = authService.login(dto);
        log.info("<-- Login Successful | Identifier: {} | Role: {}", dto.getEmailOrPhone(), data.get("role"));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login successful",
                "role", data.get("role"),
                "user", data.get("user")
        ));
    }

    // ─── Registration ────────────────────────────────────────────────────────

    @PostMapping("/register/client")
    public ResponseEntity<?> registerClient(@Valid @RequestBody ClientSignUp dto) {
        log.info("--> Endpoint Hit: POST /api/users/register/client | Email: {}", dto.getEmailOrPhone());
        var profile = clientService.register(dto);
        log.info("<-- Client Successfully Stored in MongoDB | ID: {} | Email: {}", profile.getId(), profile.getEmailOrPhone());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Client registered successfully",
                "user", profile
        ));
    }

    @PostMapping("/register/lawyer/fresher")
    public ResponseEntity<?> registerLawyerFresher(@Valid @RequestBody LawyerFresherSignup dto) {
        log.info("--> Endpoint Hit: POST /api/users/register/lawyer/fresher | Email: {}", dto.getEmailOrPhone());
        var profile = lawyerFresherService.register(dto);
        log.info("<-- Lawyer Fresher Successfully Stored in MongoDB | ID: {} | Email: {}", profile.getId(), profile.getEmailOrPhone());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Fresher lawyer registered successfully.",
                "user", profile
        ));
    }

    @PostMapping("/register/lawyer/experienced")
    public ResponseEntity<?> registerLawyerExperienced(@Valid @RequestBody LawyerExperiencedSignup dto) {
        log.info("--> Endpoint Hit: POST /api/users/register/lawyer/experienced | Email: {}", dto.getEmailOrPhone());
        var profile = lawyerExperiencedService.register(dto);
        log.info("<-- Lawyer Experienced Successfully Stored in MongoDB | ID: {} | Email: {}", profile.getId(), profile.getEmailOrPhone());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Experienced lawyer registered successfully.",
                "user", profile
        ));
    }

    // ─── Profile Fetch ───────────────────────────────────────────────────────

    /** GET /api/users/client/{id} */
    @GetMapping("/client/{id}")
    public ResponseEntity<?> getClientById(@PathVariable String id) {
        return clientService.findById(id)
                .<ResponseEntity<?>>map(profile -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "user", profile
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "error", "Client not found"
                )));
    }

    /** GET /api/users/lawyer/fresher/{id} */
    @GetMapping("/lawyer/fresher/{id}")
    public ResponseEntity<?> getLawyerFresherById(@PathVariable String id) {
        return lawyerFresherService.findById(id)
                .<ResponseEntity<?>>map(profile -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "user", profile
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "error", "Fresher lawyer not found"
                )));
    }

    /** GET /api/users/lawyer/experienced/{id} */
    @GetMapping("/lawyer/experienced/{id}")
    public ResponseEntity<?> getLawyerExperiencedById(@PathVariable String id) {
        return lawyerExperiencedService.findById(id)
                .<ResponseEntity<?>>map(profile -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "user", profile
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "error", "Experienced lawyer not found"
                )));
    }
}
