package com.askvocate.backend.controller;

import com.askvocate.backend.entity.DocType;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.model.UserDoc;
import com.askvocate.backend.service.VerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationService verificationService;

    @Autowired
    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Upload a document for a user.
     * Method: POST
     * Content-Type: multipart/form-data
     */
    @PostMapping("/{userId}/upload")
    public ResponseEntity<?> uploadDocument(
            @PathVariable String userId,
            @RequestParam("docType") DocType docType,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }
            UserDoc doc = verificationService.uploadDocument(
                    userId, 
                    docType, 
                    file.getBytes(), 
                    file.getOriginalFilename()
            );
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload failed: " + e.getMessage());
        }
    }

    /**
     * Trigger verification submit (checks required docs and adds to queue).
     * Method: POST
     */
    @PostMapping("/{userId}/submit")
    public ResponseEntity<?> submitForVerification(@PathVariable String userId) {
        try {
            verificationService.submitForVerification(userId);
            return ResponseEntity.ok("Successfully submitted for verification");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Submission failed: " + e.getMessage());
        }
    }

    /**
     * Admin endpoint to approve or reject a verification request.
     * Method: POST
     * Content-Type: application/json
     */
    @PostMapping("/{userId}/resolve")
    public ResponseEntity<?> resolveVerification(
            @PathVariable String userId,
            @RequestBody Map<String, Object> requestBody) {
        try {
            String statusStr = (String) requestBody.get("status");
            String rejectionReason = (String) requestBody.get("rejectionReason");
            
            Verification_Status status = Verification_Status.valueOf(statusStr.toUpperCase());
            verificationService.processVerification(userId, status, rejectionReason);
            
            return ResponseEntity.ok("Verification resolved to: " + status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status value. Use: VERIFIED or REJECTED");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Resolution failed: " + e.getMessage());
        }
    }

    /**
     * Get a temporary signed view URL for a document.
     * Method: GET
     */
    @GetMapping("/{userId}/document/{docType}/url")
    public ResponseEntity<?> getSignedUrl(
            @PathVariable String userId,
            @PathVariable DocType docType) {
        try {
            String url = verificationService.getDocumentSignedUrl(userId, docType);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate URL: " + e.getMessage());
        }
    }
}
