package com.askvocate.backend.dto;

import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.model.VerificationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.List;

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
    private Map<String, String> qrDecodedData;
    private Boolean isQrVerified;
    private String qrImageSide;
    private Boolean qrPrintedMismatch;
    private List<String> qrMismatchFields;
    private Double confidenceScore;
    private Boolean autoApproved;
    private Boolean requiresManualReview;
    private String manualReviewReason;
    private Instant verifiedAt;
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

    public Map<String, String> getQrDecodedData() { return qrDecodedData; }
    public void setQrDecodedData(Map<String, String> value) { qrDecodedData = value; }
    public Boolean getIsQrVerified() { return isQrVerified; }
    public void setIsQrVerified(Boolean value) { isQrVerified = value; }
    public String getQrImageSide() { return qrImageSide; }
    public void setQrImageSide(String value) { qrImageSide = value; }
    public Boolean getQrPrintedMismatch() { return qrPrintedMismatch; }
    public void setQrPrintedMismatch(Boolean value) { qrPrintedMismatch = value; }
    public List<String> getQrMismatchFields() { return qrMismatchFields; }
    public void setQrMismatchFields(List<String> value) { qrMismatchFields = value; }
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double value) { confidenceScore = value; }
    public Boolean getAutoApproved() { return autoApproved; }
    public void setAutoApproved(Boolean value) { autoApproved = value; }
    public Boolean getRequiresManualReview() { return requiresManualReview; }
    public void setRequiresManualReview(Boolean value) { requiresManualReview = value; }
    public String getManualReviewReason() { return manualReviewReason; }
    public void setManualReviewReason(String value) { manualReviewReason = value; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant value) { verifiedAt = value; }

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
