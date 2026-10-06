package com.askvocate.backend.service;

import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.SelfieVerificationRepository;
import com.askvocate.backend.repository.UserDocumentRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;

@Service
public class SelfieVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final DocumentVerificationService documents;
    private final UserDocumentRepository documentRepository;
    private final SelfieVerificationRepository selfies;
    private final MongoTemplate mongo;
    private final CloudinaryService cloudinary;
    private final VisionVerificationClient vision;

    public SelfieVerificationService(DocumentVerificationService documents, UserDocumentRepository documentRepository,
            SelfieVerificationRepository selfies, MongoTemplate mongo, CloudinaryService cloudinary,
            VisionVerificationClient vision) {
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.selfies = selfies;
        this.mongo = mongo;
        this.cloudinary = cloudinary;
        this.vision = vision;
    }

    public SelfieVerification createChallenge(String userId) {
        documents.requireExistingLawyer(userId);
        UserDocument aadhaar = verifiedAadhaar(userId);
        if (selfies.existsByUserIdAndAadhaarDocumentIdAndAadhaarSubmissionTokenAndVerificationStatus(
                userId, aadhaar.getId(), aadhaar.getSubmissionToken(), VerificationStatus.VERIFIED)) {
            throw new DocumentVerificationException("Selfie is already verified for this Aadhaar submission.");
        }
        SelfieVerification attempt = new SelfieVerification();
        attempt.setUserId(userId);
        attempt.setAadhaarDocumentId(aadhaar.getId());
        attempt.setAadhaarSubmissionToken(aadhaar.getSubmissionToken());
        attempt.setChallengeId(UUID.randomUUID().toString());
        attempt.setExpectedTurn(RANDOM.nextBoolean() ? "left" : "right");
        attempt.setChallengeExpiresAt(Instant.now().plus(3, ChronoUnit.MINUTES));
        return selfies.save(attempt);
    }

    public SelfieVerification verify(String userId, String challengeId, MultipartFile center,
            MultipartFile turned, MultipartFile returned) {
        documents.requireExistingLawyer(userId);
        if (challengeId == null || challengeId.isBlank()) throw new DocumentVerificationException("Selfie challenge is required.");
        for (MultipartFile file : new MultipartFile[]{center, turned, returned}) {
            if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
                throw new DocumentVerificationException("All three selfie frames must be nonempty images under 10 MB.",
                        null, file == null ? null : file.getOriginalFilename());
            }
        }
        UserDocument aadhaar = verifiedAadhaar(userId);
        Instant now = Instant.now();
        Query claim = Query.query(Criteria.where("userId").is(userId).and("challengeId").is(challengeId)
                .and("aadhaarDocumentId").is(aadhaar.getId()).and("challengeConsumedAt").is(null)
                .and("aadhaarSubmissionToken").is(aadhaar.getSubmissionToken())
                .and("challengeExpiresAt").gt(now));
        SelfieVerification attempt = mongo.findAndModify(claim,
                new Update().set("challengeConsumedAt", now).set("updatedAt", now), SelfieVerification.class);
        if (attempt == null) throw new DocumentVerificationException("Selfie challenge is invalid, expired, or already used.");
        attempt.setChallengeConsumedAt(now);
        try {
            List<CloudinaryRef> references = new ArrayList<>(aadhaar.getCloudinaryReferences());
            references.sort(Comparator.comparing(ref -> "back".equals(ref.getLabel()) ? 0 : 1));
            byte[] qrImage = null;
            String qrFailure = "No signed Aadhaar QR matching the printed details was found.";
            for (CloudinaryRef reference : references) {
                try {
                    byte[] candidate = cloudinary.downloadReference(reference);
                    VisionVerificationClient.QrResult qr = vision.verifyAadhaarQr(candidate,
                            aadhaar.getExtractedData(), aadhaar.getMaskedDocumentNumber());
                    if (qr.matched()) {
                        qrImage = candidate;
                        break;
                    }
                    if (qr.reason() != null) qrFailure = qr.reason();
                } catch (Exception error) {
                    qrFailure = "Stored Aadhaar image could not be read for selfie verification.";
                }
            }
            if (qrImage == null) throw new DocumentVerificationException(qrFailure);
            VisionVerificationClient.SelfieResult result = vision.verifySelfie(qrImage,
                    List.of(center.getBytes(), turned.getBytes(), returned.getBytes()), attempt.getExpectedTurn());
            attempt.setFaceMatchScore(result.faceMatchScore());
            attempt.setFaceMatchThreshold(result.faceMatchThreshold());
            attempt.setLivenessPassed(result.livenessPassed());
            attempt.setLivenessScore(result.livenessScore());
            attempt.setFailureReason(result.reason());
            if (Boolean.TRUE.equals(result.matched())) {
                CloudinaryRef uploaded = cloudinary.uploadSelfie(center,
                        "askvocate/documents/" + userId + "/SELFIE");
                attempt.setSelfieImageUrl(uploaded.getSecureUrl());
                attempt.setSelfiePublicId(uploaded.getPublicId());
                attempt.setVerificationStatus(VerificationStatus.VERIFIED);
                attempt.setVerifiedAt(Instant.now());
            }
        } catch (Exception e) {
            attempt.setFailureReason(e instanceof DocumentVerificationException
                    ? e.getMessage() : "Selfie verification could not complete: " + e.getClass().getSimpleName());
        }
        attempt.setUpdatedAt(Instant.now());
        SelfieVerification saved = selfies.save(attempt);
        if (saved.getVerificationStatus() == VerificationStatus.VERIFIED) documents.refreshLawyerStatus(userId);
        return saved;
    }

    private UserDocument verifiedAadhaar(String userId) {
        return documentRepository.findByUserIdAndDocumentType(userId, DocumentType.AADHAAR)
                .filter(doc -> doc.getVerificationStatus() == VerificationStatus.VERIFIED
                        && doc.getId() != null
                        && doc.getSubmissionToken() != null
                        && doc.getCloudinaryReferences() != null && !doc.getCloudinaryReferences().isEmpty())
                .orElseThrow(() -> new DocumentVerificationException(
                        "Aadhaar front and back OCR must be complete before selfie verification."));
    }
}
