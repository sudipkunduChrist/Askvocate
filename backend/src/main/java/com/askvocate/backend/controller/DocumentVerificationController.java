package com.askvocate.backend.controller;

import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.service.DocumentVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for identity document verification.
 * 
 * <p>All endpoints require a valid JWT bearer token. The authenticated user's
 * ID is extracted from the token's {@code sub} claim — it is <b>never</b>
 * accepted from request parameters or the body.
 * 
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/documents/verify} — submit a document for verification</li>
 *   <li>{@code GET  /api/documents}        — list all user's verified documents</li>
 *   <li>{@code GET  /api/documents/{id}}   — get a specific document by ID</li>
 * </ul>
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
     * Submit a document for identity verification.
     * 
     * <p>Request must be {@code multipart/form-data} with:
     * <ul>
     *   <li>{@code documentType} — one of: AADHAAR, PAN, DRIVING_LICENSE</li>
     *   <li>{@code front} — front image of the document (required)</li>
     *   <li>{@code back}  — back image of the document (required for AADHAAR)</li>
     * </ul>
     *
     * @param jwt          the authenticated user's JWT token (injected by Spring Security)
     * @param documentType the type of identity document
     * @param front        front image file
     * @param back         back image file (required for Aadhaar, optional for PAN/DL)
     * @return verification result with masked document number and extracted fields
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVerificationResponse> verifyDocument(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("documentType") String documentType,
            @RequestParam("front") MultipartFile front,
            @RequestParam(value = "back", required = false) MultipartFile back) {

        String userId = extractUserId(jwt);
        DocumentType type = parseDocumentType(documentType);

        log.info("Document verification request: userId={}, documentType={}", userId, type);

        DocumentVerificationResponse response =
                verificationService.verifyDocument(userId, type, front, back);

        HttpStatus status = switch (response.getVerificationStatus()) {
            case VERIFIED -> HttpStatus.OK;
            case FAILED   -> HttpStatus.UNPROCESSABLE_ENTITY;
            default       -> HttpStatus.ACCEPTED;
        };

        return ResponseEntity.status(status).body(response);
    }

    /**
     * List all verification documents for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<DocumentVerificationResponse>> getUserDocuments(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = extractUserId(jwt);
        List<DocumentVerificationResponse> documents = verificationService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get a specific verification document by ID.
     * Returns 404 if the document does not exist or belongs to another user.
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentVerificationResponse> getDocument(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String documentId) {

        String userId = extractUserId(jwt);
        DocumentVerificationResponse response = verificationService.getDocumentById(userId, documentId);
        return ResponseEntity.ok(response);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Extracts the user ID from the JWT {@code sub} claim.
     * Never trusts client-supplied user identifiers.
     */
    private String extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new DocumentVerificationException("Authentication is required.");
        }
        return jwt.getSubject();
    }

    /**
     * Parses and validates the document type string.
     */
    private DocumentType parseDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new DocumentVerificationException(
                    "Document type is required. Supported types: AADHAAR, PAN, DRIVING_LICENSE.");
        }
        try {
            return DocumentType.valueOf(documentType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new DocumentVerificationException(
                    "Invalid document type: '" + documentType
                    + "'. Supported types: AADHAAR, PAN, DRIVING_LICENSE.");
        }
    }
}
