package com.askvocate.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
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
 * {@link DocumentType}. OCR text is used transiently during verification and is
 * not persisted; full document numbers remain separate from the masked display number.
 */
@Getter
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

    /** Full number read from the Aadhaar front by OCR; it may contain OCR errors. */
    @Setter
    @JsonIgnore
    private String aadhaarNumber;

    /** Keyed digest for duplicate detection; never a plain SHA-256 of the number. */
    @Setter
    @JsonIgnore
    private String aadhaarNumberHash;

    /** Full PAN for a verified individual PAN card. */
    @Setter
    @JsonIgnore
    private String panNumber;

    /** Keyed digest of the full PAN for duplicate detection. */
    @Setter
    @JsonIgnore
    @Indexed
    private String panNumberHash;

    /** Signed PII decoded from the PAN Enhanced Secure QR. */
    @Setter
    @JsonIgnore
    private Map<String, String> panQrData;
    @Setter
    @JsonIgnore
    private String panQrRawText;
    @Setter
    private Boolean isPanQrVerified;
    @Setter
    private String panQrPhotoUrl;
    @Setter
    @JsonIgnore
    private String panQrPhotoPublicId;
    @Setter
    private Integer panQrVersion;

    /** PAN fields that did not match the signed Aadhaar QR (e.g. {@code name}, {@code dob}). */
    @Setter
    private List<String> crossDocumentMismatchFields;

    /** Admin account that reviewed this document. */
    @Setter
    private String reviewedBy;
    @Setter
    private Instant reviewedAt;
    /** {@code APPROVED} or {@code REJECTED}. */
    @Setter
    private String reviewDecision;
    @Setter
    private String reviewNotes;

    /** References to the uploaded images on Cloudinary. */
    @Setter
    private List<CloudinaryRef> cloudinaryReferences = new ArrayList<>();

    /** OCR confidence score (0.0–1.0). */
    @Setter
    private Double ocrConfidence;

    /** Data decoded from a signature-verified UIDAI QR, excluding the photograph. */
    @Setter
    private Map<String, String> qrDecodedData;
    /** Readable, signature-verified data decoded from the Aadhaar Secure QR (see {@link QrData}). */
    @Setter
    @JsonIgnore
    private QrData qrData;
    /** Raw decimal payload exactly as scanned from the Aadhaar Secure QR. */
    @Setter
    @JsonIgnore
    private String qrRawText;
    /** JSON text of the demographic fields extracted from the signed Aadhaar Secure QR. */
    @Setter
    @JsonIgnore
    private String qrText;
    @Setter
    private Boolean isQrVerified;
    @Setter
    @JsonIgnore
    private String submissionToken;
    @Setter
    private String qrImageSide;
    @Setter
    private Boolean qrPrintedMismatch;
    @Setter
    private List<String> qrMismatchFields;
    @Setter
    private Double confidenceScore;
    @Setter
    private Boolean autoApproved;
    @Setter
    private Boolean requiresManualReview;
    @Setter
    private String manualReviewReason;
    @Setter
    private Instant verifiedAt;

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

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
        this.updatedAt = Instant.now();
    }

}
