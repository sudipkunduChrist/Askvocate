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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Dedicated controller for lawyer credential & document verification.
 * Base path: /api/lawyers/verification
 */
@RestController
@RequestMapping("/api/lawyers/verification")
public class LawyerVerificationController {

    private static final Logger log = LoggerFactory.getLogger(LawyerVerificationController.class);

    private final DocumentVerificationService verificationService;
    private final com.askvocate.backend.service.BarCouncilValidationService barCouncilValidationService;

    public LawyerVerificationController(DocumentVerificationService verificationService,
                                        com.askvocate.backend.service.BarCouncilValidationService barCouncilValidationService) {
        this.verificationService = verificationService;
        this.barCouncilValidationService = barCouncilValidationService;
    }

    /**
     * GET /api/lawyers/verification/lookup-state?code=MAH
     * Resolves State and Country automatically from a Bar Council state code.
     */
    @GetMapping("/lookup-state")
    public ResponseEntity<java.util.Map<String, Object>> lookupStateFromCode(
            @RequestParam("code") String code) {
        String state = barCouncilValidationService.getStateFromCode(code);
        return ResponseEntity.ok(java.util.Map.of(
                "stateCode", code.toUpperCase(),
                "state", state,
                "country", "India"
        ));
    }

    /**
     * GET /api/lawyers/verification/lookup-codes?state=Maharashtra
     * Resolves valid Bar Council state codes from a State name.
     */
    @GetMapping("/lookup-codes")
    public ResponseEntity<java.util.Map<String, Object>> lookupCodesFromState(
            @RequestParam("state") String state) {
        java.util.List<String> codes = barCouncilValidationService.getCodesFromState(state);
        return ResponseEntity.ok(java.util.Map.of(
                "state", state,
                "country", "India",
                "validBarCouncilCodes", codes
        ));
    }

    /**
     * POST /api/lawyers/verification/verify-document
     * Uploads and verifies a lawyer document (Bar Council ID/Certificate, Aadhaar, PAN).
     */
    @PostMapping(value = "/verify-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVerificationResponse> verifyLawyerDocument(
            @RequestParam("userId") String userId,
            @RequestParam("documentType") String documentType,
            @RequestParam("front") MultipartFile front,
            @RequestParam(value = "back", required = false) MultipartFile back) {

        DocumentType type = parseDocumentType(documentType);
        log.info("Lawyer document verification: userId={}, documentType={}", userId, type);

        DocumentVerificationResponse response =
                verificationService.verifyDocument(userId, type, front, back);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/lawyers/verification/verify-bar-council
     * Direct Bar Council Enrollment Number validation & profile update.
     */
    @PostMapping("/verify-bar-council")
    public ResponseEntity<DocumentVerificationResponse> verifyBarCouncil(
            @Valid @RequestBody BarCouncilVerificationRequest request) {
        log.info("Lawyer Bar Council direct verification: userId={}, number={}",
                request.getUserId(), request.getBarCouncilNumber());

        DocumentVerificationResponse response = verificationService.verifyBarCouncilDirect(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/lawyers/verification/{userId}/summary
     * Returns full verification state, checklist, and progress for the lawyer.
     */
    @GetMapping("/{userId}/summary")
    public ResponseEntity<LawyerVerificationSummaryResponse> getVerificationSummary(
            @PathVariable String userId) {
        LawyerVerificationSummaryResponse summary = verificationService.getLawyerVerificationSummary(userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/lawyers/verification/{userId}/documents
     * Returns all verification documents submitted by this lawyer.
     */
    @GetMapping("/{userId}/documents")
    public ResponseEntity<List<DocumentVerificationResponse>> getLawyerDocuments(
            @PathVariable String userId) {
        List<DocumentVerificationResponse> documents = verificationService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    private DocumentType parseDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new DocumentVerificationException(
                    "Document type is required. Supported types: BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE, AADHAAR, PAN.");
        }
        try {
            return DocumentType.valueOf(documentType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new DocumentVerificationException(
                    "Invalid document type: '" + documentType
                    + "'. Supported types: BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE, AADHAAR, PAN.");
        }
    }
}
