package com.askvocate.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document representing a user's identity verification submission.
 * 
 * <p>Stored in the {@code documents} collection. Each record is scoped to a
 * single {@code userId} (from the JWT {@code sub} claim) and a single
 * {@link DocumentType}. Sensitive data (full document numbers, raw OCR text)
 * is <b>never</b> persisted — only masked numbers and parsed fields are stored.
 */
@Document(collection = "documents")
public class UserDocument {

    @Id
    private String id;

    /** User identifier extracted from the JWT {@code sub} claim. */
    @Indexed
    private String userId;

    /** The type of identity document submitted. */
    private DocumentType documentType;

    /** Current verification status. */
    private VerificationStatus verificationStatus;

    /**
     * Parsed, validated fields extracted from OCR.
     * 
     * <p>Example keys: {@code name}, {@code dob}, {@code gender}, {@code address}.
     * Full document numbers are <b>never</b> included here — see
     * {@link #maskedDocumentNumber} instead.
     */
    private Map<String, String> extractedData = new HashMap<>();

    /**
     * Masked document number for display purposes.
     * 
     * <p>Examples: {@code XXXX-XXXX-1234} (Aadhaar), {@code XXXXXX6789} (PAN).
     */
    private String maskedDocumentNumber;

    /** References to the uploaded images on Cloudinary. */
    private List<CloudinaryRef> cloudinaryReferences = new ArrayList<>();

    /** OCR confidence score (0.0–1.0). Raw OCR text is never stored. */
    private Double ocrConfidence;

    /** Reason for failure if {@link #verificationStatus} is {@code FAILED}. */
    private String failureReason;

    private Instant createdAt;

    private Instant updatedAt;

    public UserDocument() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── Getters & Setters ───────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
        this.updatedAt = Instant.now();
    }

    public Map<String, String> getExtractedData() {
        return extractedData;
    }

    public void setExtractedData(Map<String, String> extractedData) {
        this.extractedData = extractedData;
    }

    public String getMaskedDocumentNumber() {
        return maskedDocumentNumber;
    }

    public void setMaskedDocumentNumber(String maskedDocumentNumber) {
        this.maskedDocumentNumber = maskedDocumentNumber;
    }

    public List<CloudinaryRef> getCloudinaryReferences() {
        return cloudinaryReferences;
    }

    public void setCloudinaryReferences(List<CloudinaryRef> cloudinaryReferences) {
        this.cloudinaryReferences = cloudinaryReferences;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
