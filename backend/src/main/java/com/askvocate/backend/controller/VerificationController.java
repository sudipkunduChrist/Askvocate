package com.askvocate.backend.controller;

import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.model.LawyerFresherProfile;
import com.askvocate.backend.service.LawyerExperiencedService;
import com.askvocate.backend.service.LawyerFresherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only controller for managing lawyer verification.
 * Handles status queries, approvals, and rejections for both lawyer types.
 * Base path: /api/admin/verification
 */
@RestController
@RequestMapping("/api/admin/verification")
public class VerificationController {

    @Autowired
    private LawyerFresherService lawyerFresherService;

    @Autowired
    private LawyerExperiencedService lawyerExperiencedService;

    // ─── Status Queries ──────────────────────────────────────────────────────

    /**
     * GET /api/admin/verification/pending
     * Returns all pending lawyers (fresher + experienced) awaiting review.
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getAllPending() {
        try {
            List<LawyerFresherProfile> freshers =
                    lawyerFresherService.findByVerifStatus(Verification_Status.PENDING);
            List<LawyerExperiencedProfile> experienced =
                    lawyerExperiencedService.findByVerifStatus(Verification_Status.PENDING);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "freshers", freshers,
                    "experienced", experienced,
                    "totalCount", freshers.size() + experienced.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to fetch pending list: " + e.getMessage()
            ));
        }
    }

    /**
     * GET /api/admin/verification/fresher/status/{status}
     * Returns all fresher lawyers with the given status (PENDING, UNDER_VERIFICATION, VERIFIED, REJECTED).
     */
    @GetMapping("/fresher/status/{status}")
    public ResponseEntity<?> getFreshersByStatus(@PathVariable String status) {
        try {
            Verification_Status verifyStatus = Verification_Status.valueOf(status.toUpperCase());
            List<LawyerFresherProfile> profiles = lawyerFresherService.findByVerifStatus(verifyStatus);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", verifyStatus,
                    "profiles", profiles,
                    "count", profiles.size()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Invalid status. Valid values: PENDING, UNDER_VERIFICATION, VERIFIED, REJECTED"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to fetch profiles: " + e.getMessage()
            ));
        }
    }

    /**
     * GET /api/admin/verification/experienced/status/{status}
     * Returns all experienced lawyers with the given status.
     */
    @GetMapping("/experienced/status/{status}")
    public ResponseEntity<?> getExperiencedByStatus(@PathVariable String status) {
        try {
            Verification_Status verifyStatus = Verification_Status.valueOf(status.toUpperCase());
            List<LawyerExperiencedProfile> profiles = lawyerExperiencedService.findByVerifStatus(verifyStatus);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", verifyStatus,
                    "profiles", profiles,
                    "count", profiles.size()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Invalid status. Valid values: PENDING, UNDER_VERIFICATION, VERIFIED, REJECTED"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Failed to fetch profiles: " + e.getMessage()
            ));
        }
    }

    // ─── Approve ─────────────────────────────────────────────────────────────

    /**
     * PATCH /api/admin/verification/fresher/{id}/approve
     * Approves a fresher lawyer — sets status to "VERIFIED".
     */
    @PatchMapping("/fresher/{id}/approve")
    public ResponseEntity<?> approveFresher(@PathVariable String id) {
        try {
            LawyerFresherProfile approved = lawyerFresherService.approve(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Fresher lawyer approved successfully",
                    "user", approved
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Approval failed: " + e.getMessage()
            ));
        }
    }

    /**
     * PATCH /api/admin/verification/experienced/{id}/approve
     * Approves an experienced lawyer — sets status to "VERIFIED".
     */
    @PatchMapping("/experienced/{id}/approve")
    public ResponseEntity<?> approveExperienced(@PathVariable String id) {
        try {
            LawyerExperiencedProfile approved = lawyerExperiencedService.approve(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Experienced lawyer approved successfully",
                    "user", approved
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Approval failed: " + e.getMessage()
            ));
        }
    }

    // ─── Reject ──────────────────────────────────────────────────────────────

    /**
     * PATCH /api/admin/verification/fresher/{id}/reject
     * Rejects a fresher lawyer with a reason. Body: { "reason": "..." }
     */
    @PatchMapping("/fresher/{id}/reject")
    public ResponseEntity<?> rejectFresher(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        try {
            String reason = body.getOrDefault("reason", "No reason provided");
            LawyerFresherProfile rejected = lawyerFresherService.reject(id, reason);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Fresher lawyer rejected",
                    "user", rejected
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Rejection failed: " + e.getMessage()
            ));
        }
    }

    /**
     * PATCH /api/admin/verification/experienced/{id}/reject
     * Rejects an experienced lawyer with a reason. Body: { "reason": "..." }
     */
    @PatchMapping("/experienced/{id}/reject")
    public ResponseEntity<?> rejectExperienced(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        try {
            String reason = body.getOrDefault("reason", "No reason provided");
            LawyerExperiencedProfile rejected = lawyerExperiencedService.reject(id, reason);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Experienced lawyer rejected",
                    "user", rejected
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Rejection failed: " + e.getMessage()
            ));
        }
    }
}
