package com.askvocate.backend.repository;

import com.askvocate.backend.model.SelfieVerification;
import com.askvocate.backend.model.VerificationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface SelfieVerificationRepository extends MongoRepository<SelfieVerification, String> {
    Optional<SelfieVerification> findByUserIdAndChallengeId(String userId, String challengeId);
    boolean existsByUserIdAndAadhaarDocumentIdAndAadhaarSubmissionTokenAndVerificationStatus(
            String userId, String aadhaarDocumentId, String aadhaarSubmissionToken,
            VerificationStatus verificationStatus);
    List<SelfieVerification> findByUserIdAndAadhaarDocumentIdAndVerificationStatus(
            String userId, String aadhaarDocumentId, VerificationStatus verificationStatus);
}
