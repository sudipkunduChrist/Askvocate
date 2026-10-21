package com.askvocate.backend.controller;

import com.askvocate.backend.dto.BarCouncilVerificationRequest;
import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.dto.LawyerVerificationSummaryResponse;
import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.service.DocumentVerificationService;
import com.askvocate.backend.service.AppSessionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for lawyer identity document verification.
 * Supports Aadhaar, PAN Card, and Bar Council credentials (ID card, Enrollment Certificate, COP).
 * Client accounts are rejected; verification is only available to LAWYER_FRESHER and
 * LAWYER_EXPERIENCED accounts.
 *
 * <p>Authentication uses the signed-in app session. An optional user ID must match its owner.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentVerificationController {

    private static final Logger log = LoggerFactory.getLogger(DocumentVerificationController.class);

    private final DocumentVerificationService verificationService;
    private final AppSessionService sessions;

    public DocumentVerificationController(DocumentVerificationService verificationService,
            AppSessionService sessions) {
        this.verificationService = verificationService;
        this.sessions = sessions;
    }

    /**
     * Submit a document for identity / credential verification.
     * 
     * <p>Request must be {@code multipart/form-data} with:
     * <ul>
     *   <li>{@code documentType} — AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE</li>
     *   <li>{@code front} — front image of the document (required)</li>
     *   <li>{@code back}  — back image (required for AADHAAR; omit for PAN)</li>
     *   <li>{@code userId} — optional; if supplied it must match the app session</li>
     * </ul>
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVerificationResponse> verifyDocument(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam("documentType") String documentType,
            @RequestParam("front") MultipartFile front,
            @RequestParam(value = "back", required = false) MultipartFile back) {

        String userId = resolveUserId(authorization, paramUserId, headerUserId);
        DocumentType type = parseDocumentType(documentType);

        log.info("Document verification request: type={}, frontBytes={}, backBytes={}", type,
                front.getSize(), back == null ? 0 : back.getSize());

        DocumentVerificationResponse response =
                verificationService.verifyDocument(userId, type, front, back);

        return ResponseEntity.ok(response);
    }

    /**
     * Direct Bar Council Number verification API.
     * Validates State Bar Council enrollment number format, state code, and year,
     * instantly updating lawyer profile and recording verification status.
     */
    @PostMapping("/verify-bar-council")
    public ResponseEntity<DocumentVerificationResponse> verifyBarCouncil(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody BarCouncilVerificationRequest request) {
        requireOwner(authorization, request.getUserId());

        log.info("Direct Bar Council verification request received for signed-in account");

        DocumentVerificationResponse response = verificationService.verifyBarCouncilDirect(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get comprehensive verification summary for a lawyer.
     * Returns overall verification status, verified credentials, checklist, and next steps.
     */
    @GetMapping("/lawyer/{userId}/summary")
    public ResponseEntity<LawyerVerificationSummaryResponse> getLawyerVerificationSummary(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String userId) {
        requireOwner(authorization, userId);
        LawyerVerificationSummaryResponse summary = verificationService.getLawyerVerificationSummary(userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * List all verification documents for a specific user ID.
     */
    @GetMapping("/user/{userId}/verified")
    public ResponseEntity<List<DocumentVerificationResponse>> getVerifiedDocumentsForUser(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String userId) {
        requireOwner(authorization, userId);
        List<DocumentVerificationResponse> documents = verificationService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * List all verification documents for the signed-in account.
     */
    @GetMapping
    public ResponseEntity<List<DocumentVerificationResponse>> getUserDocuments(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId) {

        String userId = resolveUserId(authorization, paramUserId, headerUserId);
        List<DocumentVerificationResponse> documents = verificationService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get a specific verification document by ID.
     */
    @GetMapping("/details/{documentId}")
    public ResponseEntity<DocumentVerificationResponse> getDocumentDetails(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String documentId) {

        String userId = resolveUserId(authorization, null, headerUserId);

        DocumentVerificationResponse response = verificationService.getDocumentById(userId, documentId);
        if (response == null) {
            throw new DocumentVerificationException("Document not found.");
        }
        return ResponseEntity.ok(response);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void requireOwner(String authorization, String userId) {
        if (userId == null || !sessions.requireAccount(authorization).userId().equals(userId.trim())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Document account mismatch");
        }
    }

    private String resolveUserId(String authorization, String paramUserId, String headerUserId) {
        String userId = sessions.requireAccount(authorization).userId();
        if ((paramUserId != null && !paramUserId.isBlank() && !userId.equals(paramUserId.trim()))
                || (headerUserId != null && !headerUserId.isBlank() && !userId.equals(headerUserId.trim()))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Document account mismatch");
        }
        return userId;
    }

    private DocumentType parseDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new DocumentVerificationException(
                    "Document type is required. Supported types: AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE.");
        }
        try {
            return DocumentType.valueOf(documentType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new DocumentVerificationException(
                    "Invalid document type: '" + documentType
                    + "'. Supported types: AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE.");
        }
    }
}
