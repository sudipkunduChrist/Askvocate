package com.askvocate.backend.dto;

import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.model.VerificationStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO returned to the client after document verification.
 * 
 * <p><b>Privacy guarantees:</b>
 * <ul>
 *   <li>Full document numbers are <b>never</b> included — only the masked variant.</li>
 *   <li>Raw OCR text is <b>never</b> included.</li>
 *   <li>Internal Cloudinary public IDs are <b>never</b> exposed.</li>
 * </ul>
 */
public class DocumentVerificationResponse {

    private String documentId;
    private DocumentType documentType;
    private VerificationStatus verificationStatus;
    private String maskedDocumentNumber;
    private Map<String, String> extractedFields;
    private Double ocrConfidence;
    private String failureReason;
    private Instant submittedAt;

    public DocumentVerificationResponse() {
    }

    // ── Builder-style factory ───────────────────────────────────────────

    public static DocumentVerificationResponse from(
            String documentId,
            DocumentType documentType,
            VerificationStatus verificationStatus,
            String maskedDocumentNumber,
            Map<String, String> extractedFields,
            Double ocrConfidence,
            String failureReason,
            Instant submittedAt) {

        DocumentVerificationResponse response = new DocumentVerificationResponse();
        response.documentId = documentId;
        response.documentType = documentType;
        response.verificationStatus = verificationStatus;
        response.maskedDocumentNumber = maskedDocumentNumber;
        response.extractedFields = extractedFields;
        response.ocrConfidence = ocrConfidence;
        response.failureReason = failureReason;
        response.submittedAt = submittedAt;
        return response;
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public String getDocumentId() {
        return documentId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getMaskedDocumentNumber() {
        return maskedDocumentNumber;
    }

    public Map<String, String> getExtractedFields() {
        return extractedFields;
    }

    public Double getOcrConfidence() {
        return ocrConfidence;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
