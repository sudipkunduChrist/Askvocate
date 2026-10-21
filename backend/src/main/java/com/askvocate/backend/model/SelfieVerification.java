package com.askvocate.backend.model;

import com.askvocate.backend.entity.DocType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("selfie_verifications")
public class SelfieVerification {
    @Id private String id;
    @Indexed private String userId;
    private DocType documentType = DocType.SELFIE;
    private String aadhaarDocumentId;
    private String aadhaarSubmissionToken;
    private String challengeId;
    private String expectedTurn;
    private Instant challengeExpiresAt;
    private Instant challengeConsumedAt;
    private String selfieImageUrl;
    private String selfiePublicId;
    private Double faceMatchScore;
    private Double faceMatchThreshold;
    private Boolean livenessPassed;
    private Double livenessScore;
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
    private String failureReason;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant verifiedAt;
}
