package com.askvocate.backend.controller;

import com.askvocate.backend.dto.BarCouncilVerificationRequest;
import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.dto.LawyerVerificationSummaryResponse;
import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.service.DocumentVerificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for lawyer & user identity document verification.
 * Supports Aadhaar, PAN Card, and Bar Council credentials (ID card, Enrollment Certificate, COP).
 * 
 * <p>Authentication:
 * Accepts user ID from JWT principal when available, or from {@code userId} request param / {@code X-User-Id} header.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentVerificationController {

    private static final Logger log = LoggerFactory.getLogger(DocumentVerificationController.class);

    private final DocumentVerificationService verificationService;

    public DocumentVerificationController(DocumentVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Submit a document for identity / credential verification.
     * 
     * <p>Request must be {@code multipart/form-data} with:
     * <ul>
     *   <li>{@code documentType} — AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE, DRIVING_LICENSE</li>
     *   <li>{@code front} — front image of the document (required)</li>
     *   <li>{@code back}  — back image of the document (required for AADHAAR, optional for others)</li>
     *   <li>{@code userId} — optional if JWT token provided, required otherwise</li>
     * </ul>
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVerificationResponse> verifyDocument(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam("documentType") String documentType,
            @RequestParam("front") MultipartFile front,
            @RequestParam(value = "back", required = false) MultipartFile back) {

        String userId = resolveUserId(jwt, paramUserId, headerUserId);
        DocumentType type = parseDocumentType(documentType);

        log.info("Document verification request: userId={}, documentType={}", userId, type);

        DocumentVerificationResponse response =
                verificationService.verifyDocument(userId, type, front, back);

        HttpStatus status = switch (response.getVerificationStatus()) {
            case VERIFIED -> HttpStatus.OK;
            case FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case PENDING -> HttpStatus.ACCEPTED;
            case REJECTED -> HttpStatus.FORBIDDEN;
        };

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Direct Bar Council Number verification API.
     * Validates State Bar Council enrollment number format, state code, and year,
     * instantly updating lawyer profile and recording verification status.
     */
    @PostMapping("/verify-bar-council")
    public ResponseEntity<DocumentVerificationResponse> verifyBarCouncil(
            @Valid @RequestBody BarCouncilVerificationRequest request) {

        log.info("Direct Bar Council verification request: userId={}, barCouncilNumber={}",
                request.getUserId(), request.getBarCouncilNumber());

        DocumentVerificationResponse response = verificationService.verifyBarCouncilDirect(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get comprehensive verification summary for a lawyer.
     * Returns overall verification status, verified credentials, checklist, and next steps.
     */
    @GetMapping("/lawyer/{userId}/summary")
    public ResponseEntity<LawyerVerificationSummaryResponse> getLawyerVerificationSummary(
            @PathVariable String userId) {
        LawyerVerificationSummaryResponse summary = verificationService.getLawyerVerificationSummary(userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * List all verification documents for a specific user ID.
     */
    @GetMapping("/user/{userId}/verified")
    public ResponseEntity<List<DocumentVerificationResponse>> getVerifiedDocumentsForUser(
            @PathVariable String userId) {
        List<DocumentVerificationResponse> documents = verificationService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * List all verification documents for the authenticated JWT user.
     */
    @GetMapping
    public ResponseEntity<List<DocumentVerificationResponse>> getUserDocuments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId) {

        String userId = resolveUserId(jwt, paramUserId, headerUserId);
        List<DocumentVerificationResponse> documents = verificationService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get a specific verification document by ID.
     */
    @GetMapping("/details/{documentId}")
    public ResponseEntity<DocumentVerificationResponse> getDocumentDetails(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String documentId) {

        String userId = null;
        try {
            userId = resolveUserId(jwt, null, headerUserId);
        } catch (Exception ignored) {
            // Allow querying without strict ownership if public/admin
        }

        DocumentVerificationResponse response = verificationService.getDocumentById(userId, documentId);
        if (response == null) {
            throw new DocumentVerificationException("Document not found.");
        }
        return ResponseEntity.ok(response);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String resolveUserId(Jwt jwt, String paramUserId, String headerUserId) {
        if (paramUserId != null && !paramUserId.isBlank()) {
            return paramUserId.trim();
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId.trim();
        }
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        throw new DocumentVerificationException("User authentication or 'userId' parameter is required.");
    }

    private DocumentType parseDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new DocumentVerificationException(
                    "Document type is required. Supported types: AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE, DRIVING_LICENSE.");
        }
        try {
            return DocumentType.valueOf(documentType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new DocumentVerificationException(
                    "Invalid document type: '" + documentType
                    + "'. Supported types: AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE, DRIVING_LICENSE.");
        }
    }
}
