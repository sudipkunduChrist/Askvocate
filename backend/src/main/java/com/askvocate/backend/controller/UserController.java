package com.askvocate.backend.controller;

import com.askvocate.backend.dto.ClientSignUp;
import com.askvocate.backend.dto.LawyerExperiencedSignup;
import com.askvocate.backend.dto.LawyerFresherSignup;
import com.askvocate.backend.service.ClientService;
import com.askvocate.backend.service.LawyerExperiencedService;
import com.askvocate.backend.service.LawyerFresherService;
import jakarta.validation.Valid;
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

    @Autowired
    private ClientService clientService;

    @Autowired
    private LawyerFresherService lawyerFresherService;

    @Autowired
    private LawyerExperiencedService lawyerExperiencedService;

    // ─── Registration ────────────────────────────────────────────────────────


    @PostMapping("/register/client")
    public ResponseEntity<?> registerClient(@Valid @RequestBody ClientSignUp dto) {
        try {
            var profile = clientService.register(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Client registered successfully",
                    "user", profile
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Registration failed: " + e.getMessage()
            ));
        }
    }


    @PostMapping("/register/lawyer/fresher")
    public ResponseEntity<?> registerLawyerFresher(@Valid @RequestBody LawyerFresherSignup dto) {
        try {
            var profile = lawyerFresherService.register(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Fresher lawyer registered successfully. Verification pending.",
                    "user", profile
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Registration failed: " + e.getMessage()
            ));
        }
    }


    @PostMapping("/register/lawyer/experienced")
    public ResponseEntity<?> registerLawyerExperienced(@Valid @RequestBody LawyerExperiencedSignup dto) {
        try {
            var profile = lawyerExperiencedService.register(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Experienced lawyer registered successfully. Verification pending.",
                    "user", profile
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Registration failed: " + e.getMessage()
            ));
        }
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
