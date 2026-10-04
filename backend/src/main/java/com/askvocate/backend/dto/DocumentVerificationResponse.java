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
 *   <li>Full Aadhaar and PAN numbers are included only in their immediate successful upload responses.</li>
 *   <li>Raw OCR text is <b>never</b> included.</li>
 *   <li>Internal Cloudinary public IDs are <b>never</b> exposed.</li>
 * </ul>
 */
public class DocumentVerificationResponse {

    private String documentId;
    private DocumentType documentType;
    private VerificationStatus verificationStatus;
    private String maskedDocumentNumber;
    private String aadhaarNumber;
    private String panNumber;
    private Map<String, String> extractedFields;
    private Double ocrConfidence;
    private String failureReason;
    private String file;
    private Instant submittedAt;

    // Additional Lawyer Verification context
    private String lawyerVerificationStatus;
    private Boolean nameMatched;
    private String message;

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

    public static DocumentVerificationResponse from(
            String documentId,
            DocumentType documentType,
            VerificationStatus verificationStatus,
            String maskedDocumentNumber,
            Map<String, String> extractedFields,
            Double ocrConfidence,
            String failureReason,
            Instant submittedAt,
            String lawyerVerificationStatus,
            Boolean nameMatched,
            String message) {

        DocumentVerificationResponse response = from(
                documentId, documentType, verificationStatus, maskedDocumentNumber,
                extractedFields, ocrConfidence, failureReason, submittedAt
        );
        response.lawyerVerificationStatus = lawyerVerificationStatus;
        response.nameMatched = nameMatched;
        response.message = message;
        return response;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getMaskedDocumentNumber() {
        return maskedDocumentNumber;
    }

    public void setMaskedDocumentNumber(String maskedDocumentNumber) {
        this.maskedDocumentNumber = maskedDocumentNumber;
    }

    public String getAadhaarNumber() {
        return aadhaarNumber;
    }

    public void setAadhaarNumber(String aadhaarNumber) {
        this.aadhaarNumber = aadhaarNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public Map<String, String> getExtractedFields() {
        return extractedFields;
    }

    public void setExtractedFields(Map<String, String> extractedFields) {
        this.extractedFields = extractedFields;
    }

    public Double getOcrConfidence() {
        return ocrConfidence;
    }

    public void setOcrConfidence(Double ocrConfidence) {
        this.ocrConfidence = ocrConfidence;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getLawyerVerificationStatus() {
        return lawyerVerificationStatus;
    }

    public void setLawyerVerificationStatus(String lawyerVerificationStatus) {
        this.lawyerVerificationStatus = lawyerVerificationStatus;
    }

    public Boolean getNameMatched() {
        return nameMatched;
    }

    public void setNameMatched(Boolean nameMatched) {
        this.nameMatched = nameMatched;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
