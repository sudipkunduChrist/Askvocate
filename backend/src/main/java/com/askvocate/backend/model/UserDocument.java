package com.askvocate.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;
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
 * {@link DocumentType}. Raw OCR text is not persisted; Aadhaar records store
 * validated full Aadhaar or PAN numbers separately from the masked display number.
 */
@Document(collection = "documents")
public class UserDocument {

    @Setter
    @Id
    private String id;

    /** User identifier extracted from the JWT {@code sub} claim. */
    @Setter
    @Indexed
    private String userId;

    /** The type of identity document submitted. */
    @Setter
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
    @Setter
    private Map<String, String> extractedData = new HashMap<>();

    /**
     * Masked document number for display purposes.
     * 
     * <p>Examples: {@code XXXX-XXXX-1234} (Aadhaar), {@code XXXXXX6789} (PAN).
     */
    @Setter
    private String maskedDocumentNumber;

    /** Full checksum-valid Aadhaar number for a verified Aadhaar record. */
    @Setter
    @JsonIgnore
    private String aadhaarNumber;

    /** Full PAN for a verified individual PAN card. */
    @Setter
    @JsonIgnore
    private String panNumber;

    /** References to the uploaded images on Cloudinary. */
    @Setter
    private List<CloudinaryRef> cloudinaryReferences = new ArrayList<>();

    /** OCR confidence score (0.0–1.0). Raw OCR text is never stored. */
    @Setter
    private Double ocrConfidence;

    /** Reason for failure if {@link #verificationStatus} is {@code FAILED}. */
    @Setter
    private String failureReason;
    @Setter
    private String failureFile;

    @Setter
    private Instant createdAt;

    @Setter
    private Instant updatedAt;

    public UserDocument() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }



    // ── Getters & Setters ───────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public DocumentType getDocumentType() {
        return documentType;
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

    public String getMaskedDocumentNumber() {
        return maskedDocumentNumber;
    }

    public String getAadhaarNumber() {
        return aadhaarNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public List<CloudinaryRef> getCloudinaryReferences() {
        return cloudinaryReferences;
    }

    public Double getOcrConfidence() {
        return ocrConfidence;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailureFile() {
        return failureFile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
