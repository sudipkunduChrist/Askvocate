package com.askvocate.backend.service;

import com.askvocate.backend.dto.BarCouncilVerificationRequest;
import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.dto.LawyerVerificationSummaryResponse;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.entity.Role;
import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.ClientProfileRepository;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import com.askvocate.backend.repository.UserDocumentRepository;
import com.askvocate.backend.repository.SelfieVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates document verification for lawyer accounts.
 * Supports Aadhaar, PAN card, and Bar Council credentials (ID card, Enrollment Certificate, COP).
 * Only LAWYER_FRESHER and LAWYER_EXPERIENCED accounts may submit or view verification documents.
 * Automatically updates lawyer profile verification status and barCouncilId upon successful verification.
 */
@Service
public class DocumentVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentVerificationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PAN_STRUCTURE =
            Pattern.compile("[A-Z]{3}[PCHFATBLJG][A-Z][0-9]{4}[A-Z]");

    private final CloudinaryService cloudinaryService;
    private final OcrExtractionService ocrExtractionService;
    private final BarCouncilValidationService barCouncilValidationService;
    private final UserDocumentRepository documentRepository;
    private final LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository;
    private final LawyerFresherProfileRepository lawyerFresherProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final VisionVerificationClient visionVerificationClient;
    private final SelfieVerificationRepository selfieVerificationRepository;
    private final String aadhaarHashKey;

    public DocumentVerificationService(CloudinaryService cloudinaryService,
                                       OcrExtractionService ocrExtractionService,
                                       BarCouncilValidationService barCouncilValidationService,
                                       UserDocumentRepository documentRepository,
                                       LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository,
                                       LawyerFresherProfileRepository lawyerFresherProfileRepository,
                                       ClientProfileRepository clientProfileRepository,
                                       VisionVerificationClient visionVerificationClient,
                                       SelfieVerificationRepository selfieVerificationRepository,
                                       @Value("${verification.aadhaar-hash-key:}") String aadhaarHashKey) {
        this.cloudinaryService = cloudinaryService;
        this.ocrExtractionService = ocrExtractionService;
        this.barCouncilValidationService = barCouncilValidationService;
        this.documentRepository = documentRepository;
        this.lawyerExperiencedProfileRepository = lawyerExperiencedProfileRepository;
        this.lawyerFresherProfileRepository = lawyerFresherProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.visionVerificationClient = visionVerificationClient;
        this.selfieVerificationRepository = selfieVerificationRepository;
        this.aadhaarHashKey = aadhaarHashKey;
    }

    /**
     * Verifies a user's identity or professional document.
     *
     * @param userId       user identifier
     * @param documentType document type (AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, etc.)
     * @param frontImage   front side image
     * @param backImage    back side image (required for Aadhaar, not accepted for PAN)
     * @return verification response DTO with masked number and extracted fields
     */
    public DocumentVerificationResponse verifyDocument(String userId,
                                                        DocumentType documentType,
                                                        MultipartFile frontImage,
                                                        MultipartFile backImage) {
        // 1. Validate inputs
        validateRequest(userId, documentType, frontImage, backImage);
        try {
            frontImage = DocumentImageCompressor.compressIfNeeded(frontImage);
            if (backImage != null && !backImage.isEmpty()) backImage = DocumentImageCompressor.compressIfNeeded(backImage);
        } catch (java.io.IOException compressionError) {
            log.warn("Document image compression failed: type={}, reason={}", documentType, compressionError.getMessage());
            throw new DocumentVerificationException(compressionError.getMessage(), compressionError, safeFileName(frontImage));
        }
        rejectCompletedLawyerVerification(userId);

        String folder = "askvocate/documents/" + userId + "/" + documentType.name();
        List<CloudinaryRef> cloudinaryRefs = new ArrayList<>();
        OcrExtractionService.ExtractionResult extractionResult;
        String failureFile = null;
        String currentFile = safeFileName(frontImage);

        try {
            // 2. Upload front image with OCR
            log.info("Uploading front image for documentType={}, bytes={}", documentType, frontImage.getSize());
            CloudinaryService.UploadResult frontResult = uploadWithOcr(frontImage, folder, "front");
            cloudinaryRefs.add(frontResult.cloudinaryRef());

            // 3. Extract OCR from front
            OcrExtractionService.ExtractionResult frontExtraction =
                    ocrExtractionService.extract(frontResult.rawOcrData(), documentType, "front");
            if (!frontExtraction.success()) failureFile = currentFile;

            // 4. Upload and extract back image if provided
            if (backImage != null && !backImage.isEmpty()) {
                currentFile = safeFileName(backImage);
                log.info("Uploading back image for documentType={}, bytes={}", documentType, backImage.getSize());
                CloudinaryService.UploadResult backResult = uploadWithOcr(backImage, folder, "back");
                cloudinaryRefs.add(backResult.cloudinaryRef());

                OcrExtractionService.ExtractionResult backExtraction =
                        ocrExtractionService.extract(backResult.rawOcrData(), documentType, "back");
                if (!backExtraction.success() && failureFile == null) failureFile = currentFile;

                // Merge front + back results
                extractionResult = documentType == DocumentType.AADHAAR
                        ? ocrExtractionService.mergeAadhaarResults(frontExtraction, backExtraction)
                        : ocrExtractionService.mergeResults(frontExtraction, backExtraction);
            } else {
                extractionResult = frontExtraction;
            }

        } catch (Exception e) {
            // Clean up any uploaded assets on failure
            try {
                cleanupCloudinaryAssets(cloudinaryRefs);
            } catch (Exception cleanupException) {
                log.warn("Failed to clean up Cloudinary assets after verification failure", cleanupException);
            }
            if (e instanceof DocumentVerificationException verificationError) {
                throw new DocumentVerificationException(verificationError.getMessage(), verificationError, currentFile);
            }
            throw new DocumentVerificationException("Document upload or OCR processing failed. Please try again.",
                    e, currentFile);
        }

        // 4b. Read and signature-verify the Aadhaar Secure QR. For Aadhaar the signed QR is
        // the trusted source; OCR alone cannot mark the document VERIFIED.
        AadhaarQrScan qrScan = documentType == DocumentType.AADHAAR
                ? readAadhaarQr(backImage, frontImage, extractionResult.extractedFields(),
                        extractionResult.maskedDocumentNumber())
                : null;
        QrData qrData = qrScan == null ? null : qrScan.data();
        boolean qrVerified = qrScan != null && qrScan.verified();
        boolean qrMismatch = qrVerified && qrScan.printedMismatch();
        boolean ocrSuccess = extractionResult.success();
        PanQrAttempt panQrAttempt = documentType == DocumentType.PAN
                ? readPanQr(frontImage, userId) : new PanQrAttempt(null, null);
        PanQrScan panQrScan = panQrAttempt.scan();

        // 5. Determine verification status. Aadhaar requires a valid signed QR whose printed
        // fields match the OCR read. A mismatch goes to manual review; a missing or invalid QR
        // requires a re-upload.
        VerificationStatus status;
        PanEvaluation panEvaluation = null;
        if (documentType == DocumentType.PAN) {
            if (panQrScan != null) {
                panEvaluation = evaluatePanWithQr(userId,
                        ocrSuccess ? extractionResult.extractedFields() : null,
                        extractionResult.panNumber(), extractionResult.confidence(),
                        panQrScan.data());
                status = panEvaluation.status();
            } else {
                // No signed QR: never auto-verify PAN from OCR alone. Keep the submitted
                // image and route the submission to manual review.
                status = VerificationStatus.PENDING;
            }
        } else if (documentType != DocumentType.AADHAAR) {
            status = ocrSuccess ? VerificationStatus.VERIFIED : VerificationStatus.FAILED;
        } else if (qrVerified && !qrMismatch && ocrSuccess) {
            status = VerificationStatus.VERIFIED;
        } else if (qrVerified) {
            status = VerificationStatus.PENDING;
        } else {
            // No usable signed QR: the admin can review the uploaded card images manually.
            status = VerificationStatus.PENDING;
        }

        String aadhaarFailureReason = null;
        String manualReviewReason = null;
        if (documentType == DocumentType.PAN && status == VerificationStatus.PENDING) {
            if (panEvaluation != null) {
                manualReviewReason = panEvaluation.manualReason();
            } else {
                manualReviewReason = "PAN QR could not be read. Retry the scan or upload a clearer "
                        + "photo of the QR, or upload your e-PAN.";
                if (panQrAttempt.failureReason() != null && !panQrAttempt.failureReason().isBlank()) {
                    manualReviewReason += " " + panQrAttempt.failureReason();
                }
            }
        }
        if (documentType == DocumentType.AADHAAR) {
            if (status == VerificationStatus.PENDING) {
                if (!qrVerified) {
                    String qrReason = qrScan != null && qrScan.reason() != null && !qrScan.reason().isBlank()
                            ? qrScan.reason()
                            : "Aadhaar Secure QR could not be verified; upload a clear photo of the QR side.";
                    manualReviewReason = "Aadhaar QR could not be read. Re-upload a clearer photo of the QR side, "
                            + "or upload your free e-Aadhaar from uidai.gov.in. Manual review is not available "
                            + "for an unreadable QR. " + qrReason;
                    if (!ocrSuccess && extractionResult.error() != null) {
                        manualReviewReason += " " + extractionResult.error();
                    }
                } else {
                    List<String> mismatchFields = qrScan.mismatchFields();
                    manualReviewReason = qrMismatch
                            ? "Printed Aadhaar fields do not match the signed QR: "
                                + String.join(", ", mismatchFields.isEmpty()
                                    ? List.of("printed fields") : mismatchFields) + "."
                            : "Signed Aadhaar QR verified, but the printed details could not be read. "
                                + "Please re-upload a clearer photo. "
                                + Objects.toString(extractionResult.error(), "OCR extraction failed.");
                }
            } else if (status == VerificationStatus.FAILED) {
                String qrReason = qrScan != null && qrScan.reason() != null && !qrScan.reason().isBlank()
                        ? qrScan.reason()
                        : "Aadhaar Secure QR could not be verified; upload a clear photo of the QR side.";
                aadhaarFailureReason = ocrSuccess || extractionResult.error() == null
                        ? qrReason : qrReason + " " + extractionResult.error();
                if (failureFile == null && qrScan != null) {
                    failureFile = "front".equals(qrScan.side())
                            ? safeFileName(frontImage) : safeFileName(backImage);
                }
            }
        }

        // Manual review is reserved for data conflicts that a retry cannot fix.
        // Every other pending outcome is a re-upload state and auto-approves on a correct retry.
        String pendingReason = null;
        boolean requiresManualReview = false;
        if (status == VerificationStatus.PENDING) {
            if (documentType == DocumentType.AADHAAR) {
                if (!qrVerified) {
                    pendingReason = "AADHAAR_QR_UNREADABLE";
                } else if (qrMismatch) {
                    pendingReason = "AADHAAR_OCR_MISMATCH";
                    requiresManualReview = true;
                } else {
                    pendingReason = "AADHAAR_OCR_UNREADABLE";
                }
            } else if (documentType == DocumentType.PAN) {
                if (panQrScan == null) {
                    pendingReason = "PAN_QR_UNREADABLE";
                } else if (panEvaluation != null && panEvaluation.duplicate()) {
                    pendingReason = "PAN_DUPLICATE";
                    requiresManualReview = true;
                } else if (panEvaluation != null && !panEvaluation.mismatches().isEmpty()) {
                    pendingReason = "PAN_MISMATCH";
                    requiresManualReview = true;
                } else {
                    pendingReason = "PAN_WAITING_AADHAAR";
                }
            }
        }
        if ("PAN_WAITING_AADHAAR".equals(pendingReason)) {
            manualReviewReason = "PAN QR verified. Verify your Aadhaar to complete PAN verification automatically.";
        }

        // PAN evidence is kept only when the card is fully auto-verified, the QR was
        // unreadable (re-upload evidence), or it is only waiting for Aadhaar to verify.
        // Mismatch, duplicate, organization and invalid outcomes discard the uploads.
        boolean panWaitingForAadhaar = documentType == DocumentType.PAN
                && panQrScan != null
                && panEvaluation != null
                && panEvaluation.status() == VerificationStatus.PENDING
                && !panEvaluation.duplicate()
                && panEvaluation.mismatches().isEmpty();
        boolean keepPanEvidence = status == VerificationStatus.VERIFIED
                || panQrScan == null
                || panWaitingForAadhaar;
        boolean discardUploads = status == VerificationStatus.FAILED
                || (documentType == DocumentType.PAN && !keepPanEvidence);
        if (discardUploads) {
            log.info("Cleaning temporary Cloudinary assets: type={}, status={}", documentType, status);
            cleanupCloudinaryAssets(cloudinaryRefs);
            cloudinaryRefs = new ArrayList<>();
        }

        // 6. Look up existing document record or create a new one (supports re-verification)
        UserDocument userDocument = documentRepository.findByUserIdAndDocumentType(userId, documentType)
                .orElse(new UserDocument());
        String previousQrPhoto = userDocument.getQrData() == null
                ? null : userDocument.getQrData().getPhotoPublicId();
        String previousPanQrPhoto = userDocument.getPanQrPhotoPublicId();
        List<CloudinaryRef> previousRefs = userDocument.getCloudinaryReferences() == null
                ? List.of() : new ArrayList<>(userDocument.getCloudinaryReferences());

        userDocument.setUserId(userId);
        userDocument.setDocumentType(documentType);
        if (documentType == DocumentType.AADHAAR) userDocument.setSubmissionToken(UUID.randomUUID().toString());
        userDocument.setVerificationStatus(status);
        Map<String, String> panQrFields = documentType == DocumentType.PAN && panQrScan != null
                ? panQrFields(panQrScan.data()) : null;
        userDocument.setExtractedData(documentType == DocumentType.PAN && panQrFields != null
                ? panQrFields
                : documentType == DocumentType.AADHAAR || documentType == DocumentType.PAN
                    || extractionResult.success()
                    ? extractionResult.extractedFields() : Map.of());
        userDocument.setMaskedDocumentNumber(documentType == DocumentType.AADHAAR || extractionResult.success()
                ? extractionResult.maskedDocumentNumber() : null);
        userDocument.setAadhaarNumber(documentType == DocumentType.AADHAAR
                ? extractionResult.aadhaarNumber() : null);
        userDocument.setAadhaarNumberHash(documentType == DocumentType.AADHAAR
                ? hashAadhaar(extractionResult.aadhaarNumber()) : null);
        String panNumber = documentType == DocumentType.PAN
                ? (panQrScan != null ? panQrScan.data().get("pan") : extractionResult.panNumber())
                : null;
        userDocument.setPanNumber(panNumber);
        userDocument.setPanNumberHash(documentType == DocumentType.PAN ? hashPan(panNumber) : null);
        if (documentType == DocumentType.PAN && panNumber != null) {
            userDocument.setMaskedDocumentNumber(ocrExtractionService.maskPan(panNumber));
        }
        if (documentType == DocumentType.PAN) {
            // The QR-embedded photo is kept for a verified PAN or while waiting for Aadhaar.
            boolean keepQrPhoto = panQrScan != null
                    && (status == VerificationStatus.VERIFIED || panWaitingForAadhaar);
            userDocument.setPanQrData(panQrScan == null ? null : panQrScan.data());
            userDocument.setPanQrRawText(panQrScan == null ? null : panQrScan.rawText());
            userDocument.setIsPanQrVerified(panQrScan != null);
            userDocument.setPanQrVersion(panQrScan == null ? null : panQrScan.version());
            userDocument.setPanQrPhotoUrl(keepQrPhoto ? panQrScan.photoUrl() : null);
            userDocument.setPanQrPhotoPublicId(keepQrPhoto ? panQrScan.photoPublicId() : null);
            if (!keepQrPhoto && panQrScan != null && panQrScan.photoPublicId() != null) {
                try {
                    cloudinaryService.delete(panQrScan.photoPublicId());
                } catch (Exception error) {
                    log.warn("Could not delete unused PAN QR photo: {}", error.getClass().getSimpleName());
                }
            }
        }
        userDocument.setCrossDocumentMismatchFields(documentType == DocumentType.PAN
                && panEvaluation != null && !panEvaluation.mismatches().isEmpty()
                ? panEvaluation.mismatches() : null);
        userDocument.setReviewedBy(null);
        userDocument.setReviewedAt(null);
        userDocument.setReviewDecision(null);
        userDocument.setReviewNotes(null);
        boolean discardPanEvidence = documentType == DocumentType.PAN && discardUploads;
        if (discardPanEvidence) {
            // PAN images are only kept for auto-verified or QR-unreadable manual review.
            userDocument.setCloudinaryReferences(new ArrayList<>());
        } else if (status == VerificationStatus.FAILED && !previousRefs.isEmpty()) {
            // Non-PAN hard failure: keep the last reviewable evidence.
            userDocument.setCloudinaryReferences(previousRefs);
        } else {
            userDocument.setCloudinaryReferences(cloudinaryRefs);
        }
        userDocument.setOcrConfidence(extractionResult.confidence());
        userDocument.setQrData(qrData);
        userDocument.setQrRawText(qrData == null ? null : qrData.getRawText());
        userDocument.setQrText(qrText(qrData));
        userDocument.setRequiresManualReview(requiresManualReview);
        userDocument.setPendingReason(pendingReason);
        userDocument.setUpdatedAt(Instant.now());
        if (documentType == DocumentType.AADHAAR) {
            userDocument.setIsQrVerified(qrVerified);
            userDocument.setQrImageSide(qrScan == null ? null : qrScan.side());
            userDocument.setQrDecodedData(qrDecodedData(qrData));
            userDocument.setQrPrintedMismatch(qrVerified ? qrMismatch : null);
            userDocument.setQrMismatchFields(qrMismatch ? qrScan.mismatchFields() : null);
            userDocument.setManualReviewReason(manualReviewReason);
            userDocument.setAutoApproved(status == VerificationStatus.VERIFIED);
            userDocument.setConfidenceScore(extractionResult.confidence());
        }
        if (documentType == DocumentType.PAN) {
            userDocument.setManualReviewReason(manualReviewReason);
            userDocument.setAutoApproved(status == VerificationStatus.VERIFIED);
            userDocument.setConfidenceScore(extractionResult.confidence());
        }
        userDocument.setVerifiedAt(status == VerificationStatus.VERIFIED ? Instant.now() : null);

        if (status == VerificationStatus.FAILED) {
            String panFailure = documentType == DocumentType.PAN
                    && panEvaluation != null && panEvaluation.manualReason() != null
                    ? panEvaluation.manualReason() : extractionResult.error();
            if (documentType == DocumentType.PAN && panQrScan == null
                    && panQrAttempt.failureReason() != null) {
                panFailure = "PAN QR was not usable: " + panQrAttempt.failureReason()
                        + (panFailure == null ? "" : " " + panFailure);
            }
            userDocument.setFailureReason(documentType == DocumentType.AADHAAR
                    ? aadhaarFailureReason : panFailure);
            userDocument.setFailureFile(failureFile);
        } else if (status == VerificationStatus.PENDING) {
            userDocument.setFailureReason(manualReviewReason);
            userDocument.setFailureFile(null);
        } else {
            userDocument.setFailureReason(null);
            userDocument.setFailureFile(null);
        }

        UserDocument saved = documentRepository.save(userDocument);
        // Only the latest submission is kept. Replace the previous Cloudinary assets
        // (including manual-review evidence) instead of orphaning them.
        if (!cloudinaryRefs.isEmpty() || discardPanEvidence) {
            Set<String> keptIds = new HashSet<>();
            for (CloudinaryRef ref : cloudinaryRefs) {
                if (ref.getPublicId() != null) keptIds.add(ref.getPublicId());
            }
            for (CloudinaryRef old : previousRefs) {
                if (old.getPublicId() != null && !keptIds.contains(old.getPublicId())) {
                    try {
                        cloudinaryService.delete(old.getPublicId());
                    } catch (Exception error) {
                        log.warn("Could not delete replaced document image: {}", error.getClass().getSimpleName());
                    }
                }
            }
        }
        if (previousQrPhoto != null && (qrData == null
                || !previousQrPhoto.equals(qrData.getPhotoPublicId()))) {
            try {
                cloudinaryService.delete(previousQrPhoto);
            } catch (Exception error) {
                log.warn("Could not delete replaced Aadhaar QR photo: {}", error.getClass().getSimpleName());
            }
        }
        if (previousPanQrPhoto != null && (panQrScan == null
                || !previousPanQrPhoto.equals(panQrScan.photoPublicId()))) {
            try {
                cloudinaryService.delete(previousPanQrPhoto);
            } catch (Exception error) {
                log.warn("Could not delete replaced PAN QR photo: {}", error.getClass().getSimpleName());
            }
        }
        if (documentType == DocumentType.AADHAAR && status == VerificationStatus.VERIFIED) {
            reevaluatePendingPan(userId);
        }
        if (documentType == DocumentType.AADHAAR && saved.getId() != null) {
            var olderSelfies = selfieVerificationRepository.findByUserIdAndAadhaarDocumentIdAndVerificationStatus(
                    userId, saved.getId(), VerificationStatus.VERIFIED);
            for (SelfieVerification selfie : olderSelfies) {
                selfie.setVerificationStatus(VerificationStatus.PENDING);
                selfie.setVerifiedAt(null);
                selfie.setFailureReason("Aadhaar was resubmitted; capture a new live selfie.");
                selfie.setUpdatedAt(Instant.now());
                selfieVerificationRepository.save(selfie);
            }
        }
        log.info("Document verification saved: type={}, status={}, ocrConfidence={}",
                documentType, status, saved.getOcrConfidence());

        // 7. Update Lawyer Profile credentials and verification status if applicable
        LawyerUpdateResult lawyerUpdate = applyDocumentVerificationToLawyer(userId, saved);

        DocumentVerificationResponse response = toResponse(saved, lawyerUpdate);
        if (status == VerificationStatus.VERIFIED && documentType == DocumentType.AADHAAR) {
            response.setAadhaarNumber(saved.getAadhaarNumber());
        } else if (status == VerificationStatus.VERIFIED && documentType == DocumentType.PAN) {
            response.setPanNumber(saved.getPanNumber());
        }
        return response;
    }

    /**
     * Direct Bar Council Number verification API for lawyers.
     * Allows validating State Bar Council enrollment number format, state code, and year,
     * instantly updating lawyer profile and recording verification status.
     */
    public DocumentVerificationResponse verifyBarCouncilDirect(BarCouncilVerificationRequest request) {
        String userId = request.getUserId();
        validateExistingLawyer(userId);
        rejectCompletedLawyerVerification(userId);
        String rawNumber = request.getBarCouncilNumber();

        var validationResult = barCouncilValidationService.validate(rawNumber);
        if (!validationResult.valid()) {
            throw new DocumentVerificationException(validationResult.errorMessage());
        }

        Map<String, String> fields = new HashMap<>();
        fields.put("enrollmentNumber", validationResult.normalizedNumber());
        fields.put("stateCode", validationResult.stateCode());
        fields.put("state", validationResult.stateName());
        fields.put("country", validationResult.country());
        fields.put("stateCouncil", validationResult.stateCouncil());
        fields.put("enrollmentYear", String.valueOf(validationResult.enrollmentYear()));
        fields.put("sequenceNumber", validationResult.sequenceNumber());
        fields.put("verificationMethod", "DIRECT_BAR_COUNCIL_REGISTRY_VALIDATION");

        if (request.getAdvocateName() != null && !request.getAdvocateName().isBlank()) {
            fields.put("name", request.getAdvocateName().trim());
        }

        UserDocument userDocument = documentRepository.findByUserIdAndDocumentType(userId, DocumentType.BAR_COUNCIL_ID)
                .orElse(new UserDocument());

        userDocument.setUserId(userId);
        userDocument.setDocumentType(DocumentType.BAR_COUNCIL_ID);
        userDocument.setVerificationStatus(VerificationStatus.VERIFIED);
        userDocument.setMaskedDocumentNumber(validationResult.maskedNumber());
        userDocument.setExtractedData(fields);
        userDocument.setOcrConfidence(1.0);
        userDocument.setFailureReason(null);
        userDocument.setUpdatedAt(Instant.now());

        UserDocument saved = documentRepository.save(userDocument);

        // Update Lawyer profile
        LawyerUpdateResult lawyerUpdate = applyDocumentVerificationToLawyer(userId, saved);

        return toResponse(saved, lawyerUpdate);
    }

    /**
     * Returns a comprehensive verification summary for a lawyer, including overall status,
     * checklist of verified documents, and next steps.
     */
    public LawyerVerificationSummaryResponse getLawyerVerificationSummary(String userId) {
        validateExistingLawyer(userId);
        // Find lawyer
        Optional<LawyerExperiencedProfile> expOpt = findExperiencedLawyer(userId);
        Optional<LawyerFresherProfile> fresherOpt = findFresherLawyer(userId);

        String name = "Unknown";
        String email = "";
        String role = "USER";
        Verification_Status overallStatus = Verification_Status.PENDING;
        String barCouncilId = "";

        if (expOpt.isPresent()) {
            var exp = expOpt.get();
            name = exp.getName();
            email = exp.getEmail();
            role = "LAWYER_EXPERIENCED";
            overallStatus = exp.getVerificationStatus();
            barCouncilId = exp.getBarCouncilId();
        } else if (fresherOpt.isPresent()) {
            var fresher = fresherOpt.get();
            name = fresher.getName();
            email = fresher.getEmail();
            role = "LAWYER_FRESHER";
            overallStatus = fresher.getVerificationStatus();
            barCouncilId = fresher.getBarCouncilId();
        } else {
            var clientOpt = clientProfileRepository.findById(userId);
            if (clientOpt.isPresent()) {
                name = clientOpt.get().getName();
                email = clientOpt.get().getEmail();
                role = "CLIENT";
            }
        }

        List<UserDocument> documents = documentRepository.findByUserId(userId);
        List<DocumentVerificationResponse> docResponses = documents.stream()
                .map(this::toResponse)
                .toList();

        boolean barCouncilVerified = documents.stream().anyMatch(d ->
                (d.getDocumentType() == DocumentType.BAR_COUNCIL_ID
                        || d.getDocumentType() == DocumentType.BAR_CERTIFICATE
                        || d.getDocumentType() == DocumentType.CERTIFICATE_OF_PRACTICE)
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        boolean aadhaarVerified = documents.stream().anyMatch(this::isVerifiedAadhaar);

        boolean panVerified = documents.stream().anyMatch(d ->
                d.getDocumentType() == DocumentType.PAN
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        boolean selfieVerified = documents.stream().anyMatch(d -> hasVerifiedSelfie(userId, d));
        boolean identityVerified = aadhaarVerified && panVerified && selfieVerified;

        int verifiedCount = (barCouncilVerified ? 1 : 0) + (aadhaarVerified ? 1 : 0)
                + (panVerified ? 1 : 0) + (selfieVerified ? 1 : 0);
        int completionPercentage = verifiedCount * 25;
        if (verifiedCount < 4) overallStatus = Verification_Status.PENDING;

        List<String> missing = new ArrayList<>();
        if (!barCouncilVerified) missing.add("Bar Council ID / Certificate");
        if (!aadhaarVerified) missing.add("Aadhaar Card (front + back)");
        if (!panVerified) missing.add("PAN Card");
        if (!selfieVerified) missing.add("Live Selfie");

        String nextStep = missing.isEmpty()
                ? "Bar Council, Aadhaar, PAN, and live selfie are verified."
                : "Missing: " + String.join(" and ", missing) + " to achieve 100% document verification process complete (" + completionPercentage + "% completed).";

        Map<String, Object> summaryDetails = new LinkedHashMap<>();
        summaryDetails.put("totalDocumentsSubmitted", documents.size());
        summaryDetails.put("isFullyVerified", completionPercentage == 100);
        summaryDetails.put("mandatoryDocumentsRequired", 4);
        summaryDetails.put("selfieVerified", selfieVerified);
        summaryDetails.put("mandatoryDocumentsVerified", verifiedCount);

        return LawyerVerificationSummaryResponse.builder()
                .userId(userId)
                .lawyerName(name)
                .email(email)
                .role(role)
                .verificationStatus(overallStatus)
                .barCouncilId(barCouncilId)
                .completionPercentage(completionPercentage)
                .barCouncilVerified(barCouncilVerified)
                .aadhaarVerified(aadhaarVerified)
                .panVerified(panVerified)
                .identityVerified(identityVerified)
                .nextStep(nextStep)
                .verifiedDocuments(docResponses)
                .summaryDetails(summaryDetails)
                .build();
    }

    /**
     * Returns all verification documents for a user.
     */
    public List<DocumentVerificationResponse> getUserDocuments(String userId) {
        validateDocumentOwner(userId);
        return documentRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns a specific verification document by ID, checking user ownership if userId is non-empty.
     */
    public DocumentVerificationResponse getDocumentById(String userId, String documentId) {
        validateDocumentOwner(userId);
        UserDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentVerificationException("Document not found."));

        if (userId != null && !userId.isBlank() && !doc.getUserId().equals(userId)) {
            throw new DocumentVerificationException("Document not found.");
        }

        return toResponse(doc);
    }

    // ── Lawyer Profile Integration ──────────────────────────────────────

    private record LawyerUpdateResult(
            boolean isLawyer,
            Verification_Status lawyerStatus,
            Boolean nameMatched,
            String message
    ) {}

    private LawyerUpdateResult applyDocumentVerificationToLawyer(String userId, UserDocument doc) {
        Optional<LawyerExperiencedProfile> expOpt = findExperiencedLawyer(userId);
        Optional<LawyerFresherProfile> fresherOpt = findFresherLawyer(userId);

        boolean isBarCouncilDoc = doc.getDocumentType() == DocumentType.BAR_COUNCIL_ID
                || doc.getDocumentType() == DocumentType.BAR_CERTIFICATE
                || doc.getDocumentType() == DocumentType.CERTIFICATE_OF_PRACTICE;

        String extractedName = doc.getExtractedData() != null ? doc.getExtractedData().get("name") : null;
        String enrollmentNum = doc.getExtractedData() != null ? doc.getExtractedData().get("enrollmentNumber") : null;

        // Fetch all verified documents for this user to check cross-verification requirements
        List<UserDocument> allDocs = new ArrayList<>(documentRepository.findByUserId(userId));
        if (allDocs.stream().noneMatch(d -> Objects.equals(d.getId(), doc.getId()))) {
            allDocs.add(doc);
        }

        boolean hasBarCouncilVerified = allDocs.stream().anyMatch(d ->
                (d.getDocumentType() == DocumentType.BAR_COUNCIL_ID
                        || d.getDocumentType() == DocumentType.BAR_CERTIFICATE
                        || d.getDocumentType() == DocumentType.CERTIFICATE_OF_PRACTICE)
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        boolean hasAadhaarVerified = allDocs.stream().anyMatch(this::isVerifiedAadhaar);

        boolean hasPanVerified = allDocs.stream().anyMatch(d ->
                d.getDocumentType() == DocumentType.PAN
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        boolean hasSelfieVerified = allDocs.stream().anyMatch(d -> hasVerifiedSelfie(userId, d));
        int verifiedCount = (hasBarCouncilVerified ? 1 : 0) + (hasAadhaarVerified ? 1 : 0)
                + (hasPanVerified ? 1 : 0) + (hasSelfieVerified ? 1 : 0);
        int percentage = verifiedCount * 25;
        boolean isFullyVerified = verifiedCount == 4;

        List<String> remainingDocs = new ArrayList<>();
        if (!hasBarCouncilVerified) remainingDocs.add("Bar Council ID / Certificate");
        if (!hasAadhaarVerified) remainingDocs.add("Aadhaar Card");
        if (!hasPanVerified) remainingDocs.add("PAN Card");
        if (!hasSelfieVerified) remainingDocs.add("Live Selfie");

        String progressMsg = isFullyVerified
                ? "Bar Council, Aadhaar, PAN, and live selfie verified."
                : doc.getDocumentType() + " processed (" + percentage + "% complete). Missing: " + String.join(", ", remainingDocs) + ".";

        if (expOpt.isEmpty() && fresherOpt.isEmpty()) {
            throw new DocumentVerificationException("Invalid userID or lawyer role.");
        }

        if (expOpt.isPresent()) {
            LawyerExperiencedProfile exp = expOpt.get();
            boolean nameMatch = true;
            if (extractedName != null && !extractedName.isBlank()) {
                nameMatch = barCouncilValidationService.isNameMatching(extractedName, exp.getName());
            }

            if (doc.getVerificationStatus() == VerificationStatus.VERIFIED) {
                if (isBarCouncilDoc && enrollmentNum != null && !enrollmentNum.isBlank()) {
                    exp.setBarCouncilId(enrollmentNum);
                }
                exp.setVerificationStatus(isFullyVerified ? Verification_Status.VERIFIED : Verification_Status.PENDING);
                if (isFullyVerified) {
                    exp.setVerifiedAt(System.currentTimeMillis());
                    exp.setRejectionReason(null);
                }
                lawyerExperiencedProfileRepository.save(exp);

                return new LawyerUpdateResult(true, exp.getVerificationStatus(), nameMatch, progressMsg);
            } else {
                exp.setVerificationStatus(Verification_Status.PENDING);
                exp.setVerifiedAt(null);
                lawyerExperiencedProfileRepository.save(exp);
                return new LawyerUpdateResult(true, Verification_Status.PENDING, nameMatch,
                        doc.getVerificationStatus() == VerificationStatus.PENDING
                                ? "Aadhaar is pending signed QR and printed-field matching: " + doc.getManualReviewReason()
                                : "Document verification failed: " + doc.getFailureReason());
            }
        } else {
            LawyerFresherProfile fresher = fresherOpt.get();
            boolean nameMatch = true;
            if (extractedName != null && !extractedName.isBlank()) {
                nameMatch = barCouncilValidationService.isNameMatching(extractedName, fresher.getName());
            }

            if (doc.getVerificationStatus() == VerificationStatus.VERIFIED) {
                if (isBarCouncilDoc && enrollmentNum != null && !enrollmentNum.isBlank()) {
                    fresher.setBarCouncilId(enrollmentNum);
                }
                fresher.setVerificationStatus(isFullyVerified ? Verification_Status.VERIFIED : Verification_Status.PENDING);
                if (isFullyVerified) {
                    fresher.setVerifiedAt(System.currentTimeMillis());
                    fresher.setRejectionReason(null);
                }
                lawyerFresherProfileRepository.save(fresher);

                return new LawyerUpdateResult(true, fresher.getVerificationStatus(), nameMatch, progressMsg);
            } else {
                fresher.setVerificationStatus(Verification_Status.PENDING);
                fresher.setVerifiedAt(null);
                lawyerFresherProfileRepository.save(fresher);
                return new LawyerUpdateResult(true, Verification_Status.PENDING, nameMatch,
                        doc.getVerificationStatus() == VerificationStatus.PENDING
                                ? "Aadhaar is pending signed QR and printed-field matching: " + doc.getManualReviewReason()
                                : "Document verification failed: " + doc.getFailureReason());
            }
        }
    }

    private Optional<LawyerExperiencedProfile> findExperiencedLawyer(String userId) {
        return lawyerExperiencedProfileRepository.findById(userId)
                .filter(profile -> profile.getRole() == Role.LAWYER_EXPERIENCED);
    }

    private Optional<LawyerFresherProfile> findFresherLawyer(String userId) {
        return lawyerFresherProfileRepository.findById(userId)
                .filter(profile -> profile.getRole() == Role.LAWYER_FRESHER);
    }

    // OCR is required for verification. A failed upload cannot create local identity data.
    private CloudinaryService.UploadResult uploadWithOcr(MultipartFile file, String folder, String tag)
            throws Exception {
        return cloudinaryService.uploadWithOcr(file, folder, tag);
    }

    private String safeFileName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) return null;
        String name = file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replaceAll("[\\r\\n\\x00-\\x1f]", "");
    }

    private void cleanupCloudinaryAssets(List<CloudinaryRef> refs) {
        for (CloudinaryRef ref : refs) {
            try {
                if (ref.getPublicId() != null && !ref.getPublicId().startsWith("local_")) {
                    cloudinaryService.delete(ref.getPublicId());
                }
            } catch (Exception e) {
                log.error("Failed to cleanup Cloudinary asset: {}", ref.getPublicId(), e);
            }
        }
    }

    private String hashAadhaar(String number) {
        return hashIdentity(number);
    }

    private String hashPan(String number) {
        return hashIdentity(number);
    }

    private String hashIdentity(String number) {
        if (number == null || aadhaarHashKey == null || aadhaarHashKey.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(aadhaarHashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(number.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash identity number", e);
        }
    }

    // ── Validation ──────────────────────────────────────────────────────

    private void validateExistingLawyer(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DocumentVerificationException("Invalid userID.");
        }
        String roleError = "Document verification is only available for LAWYER_FRESHER and LAWYER_EXPERIENCED roles.";
        if (clientProfileRepository.existsById(userId)) {
            throw new DocumentVerificationException(roleError);
        }
        Optional<LawyerExperiencedProfile> experienced = lawyerExperiencedProfileRepository.findById(userId);
        Optional<LawyerFresherProfile> fresher = lawyerFresherProfileRepository.findById(userId);
        if (experienced.isEmpty() && fresher.isEmpty()) {
            throw new DocumentVerificationException("Invalid userID.");
        }
        if (experienced.filter(profile -> profile.getRole() == Role.LAWYER_EXPERIENCED).isEmpty()
                && fresher.filter(profile -> profile.getRole() == Role.LAWYER_FRESHER).isEmpty()) {
            throw new DocumentVerificationException(roleError);
        }
    }

    public void requireExistingLawyer(String userId) {
        validateExistingLawyer(userId);
    }

    private void rejectCompletedLawyerVerification(String userId) {
        Optional<LawyerExperiencedProfile> experienced = findExperiencedLawyer(userId);
        Optional<LawyerFresherProfile> fresher = findFresherLawyer(userId);
        boolean markedVerified = experienced.map(p -> p.getVerificationStatus() == Verification_Status.VERIFIED)
                .orElse(false) || fresher.map(p -> p.getVerificationStatus() == Verification_Status.VERIFIED).orElse(false);
        if (!markedVerified) return;

        List<UserDocument> documents = documentRepository.findByUserId(userId);
        if (hasAllRequiredDocuments(userId, documents)) {
            throw new DocumentVerificationException("Lawyer verification is already complete; re-verification is not required.");
        }

        // Repair profiles previously marked VERIFIED before all documents were complete.
        experienced.ifPresent(profile -> {
            profile.setVerificationStatus(Verification_Status.PENDING);
            profile.setVerifiedAt(null);
            lawyerExperiencedProfileRepository.save(profile);
        });
        fresher.ifPresent(profile -> {
            profile.setVerificationStatus(Verification_Status.PENDING);
            profile.setVerifiedAt(null);
            lawyerFresherProfileRepository.save(profile);
        });
    }

    private boolean hasAllRequiredDocuments(String userId, List<UserDocument> documents) {
        boolean bar = documents.stream().anyMatch(d ->
                (d.getDocumentType() == DocumentType.BAR_COUNCIL_ID
                        || d.getDocumentType() == DocumentType.BAR_CERTIFICATE
                        || d.getDocumentType() == DocumentType.CERTIFICATE_OF_PRACTICE)
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED);
        boolean aadhaar = documents.stream().anyMatch(this::isVerifiedAadhaar);
        boolean pan = documents.stream().anyMatch(d -> d.getDocumentType() == DocumentType.PAN
                && d.getVerificationStatus() == VerificationStatus.VERIFIED);
        boolean selfie = documents.stream().anyMatch(d -> hasVerifiedSelfie(userId, d));
        return bar && aadhaar && pan && selfie;
    }

    private boolean isVerifiedAadhaar(UserDocument document) {
        return document.getDocumentType() == DocumentType.AADHAAR
                && document.getVerificationStatus() == VerificationStatus.VERIFIED
                && Boolean.TRUE.equals(document.getIsQrVerified())
                && !Boolean.TRUE.equals(document.getQrPrintedMismatch());
    }

    // ── PAN verification ────────────────────────────────────────────────

    private record PanEvaluation(VerificationStatus status, List<String> mismatches,
                                 String manualReason, boolean duplicate) {}

    private record PanQrScan(Map<String, String> data, String rawText, Integer version,
                             String photoUrl, String photoPublicId) {}

    private record PanQrAttempt(PanQrScan scan, String failureReason) {}

    private PanQrAttempt readPanQr(MultipartFile frontImage, String userId) {
        if (frontImage == null || frontImage.isEmpty()) {
            return new PanQrAttempt(null, "No front image was available.");
        }
        try {
            VisionVerificationClient.PanQrResult qr = visionVerificationClient.verifyPanQr(frontImage.getBytes());
            if (qr == null || !Boolean.TRUE.equals(qr.verified())
                    || !Boolean.TRUE.equals(qr.signatureVerified())
                    || qr.data() == null || qr.data().isEmpty()) {
                String reason = qr == null || qr.reason() == null || qr.reason().isBlank()
                        ? "PAN Secure QR was not found or its signature could not be verified."
                        : qr.reason();
                log.info("PAN QR not usable: {}", reason);
                return new PanQrAttempt(null, reason);
            }
            return new PanQrAttempt(toPanQrScan(qr, userId), null);
        } catch (Exception error) {
            log.warn("PAN QR scan failed: {}", error.getClass().getSimpleName());
            return new PanQrAttempt(null,
                    "PAN QR scan failed: " + (error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }

    private PanQrScan toPanQrScan(VisionVerificationClient.PanQrResult qr, String userId) {
        String photoUrl = null;
        String photoPublicId = null;
        if (qr.photoBase64() != null && !qr.photoBase64().isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(qr.photoBase64());
                CloudinaryRef ref = cloudinaryService.uploadBytes(bytes,
                        "askvocate/documents/" + userId + "/PAN/qr", "qr");
                photoUrl = ref.getSecureUrl();
                photoPublicId = ref.getPublicId();
            } catch (Exception error) {
                log.warn("Could not store PAN QR photo: {}", error.getClass().getSimpleName());
            }
        }
        return new PanQrScan(Map.copyOf(qr.data()), qr.rawText(), qr.version(), photoUrl, photoPublicId);
    }

    /**
     * The PAN Enhanced QR is digitally signed. When it verifies, it is the trusted source,
     * but it must still belong to the same person as the verified Aadhaar QR. OCR is used
     * only as a mismatch red flag when the read is reasonably confident.
     */
    private PanEvaluation evaluatePanWithQr(String userId, Map<String, String> ocrFields,
                                            String ocrPanNumber, double ocrConfidence,
                                            Map<String, String> qrData) {
        String pan = normalizePan(qrData.get("pan"));
        String panType = qrData.getOrDefault("panType", "");
        String panValid = qrData.getOrDefault("panValid", "false");
        if (!"Individual".equalsIgnoreCase(panType)) {
            return new PanEvaluation(VerificationStatus.FAILED, List.of(),
                    "Only an individual PAN card can verify a lawyer profile.", false);
        }
        if (!"true".equalsIgnoreCase(panValid) || !PAN_STRUCTURE.matcher(pan).matches()) {
            return new PanEvaluation(VerificationStatus.FAILED, List.of(),
                    "Invalid PAN number: the fourth character is not a valid PAN holder type.", false);
        }
        String panHash = hashPan(pan);
        if (panHash != null) {
            Optional<UserDocument> duplicate = documentRepository.findByPanNumberHash(panHash);
            if (duplicate.isPresent() && !Objects.equals(userId, duplicate.get().getUserId())) {
                return new PanEvaluation(VerificationStatus.PENDING, List.of(),
                        "PAN is already registered to another account.", true);
            }
        }
        QrData aadhaarQr = verifiedAadhaarQr(userId);
        if (aadhaarQr == null) {
            return new PanEvaluation(VerificationStatus.PENDING, List.of(),
                    "Verified Aadhaar QR is required before PAN can be verified.", false);
        }
        List<String> mismatches = new ArrayList<>();
        if (!barCouncilValidationService.isNameMatching(qrData.get("name"), aadhaarQr.getName())) {
            mismatches.add("name");
        }
        if (!datesMatch(qrData.get("dob"), aadhaarQr.getDob())) {
            mismatches.add("dob");
        }
        if (ocrFields != null && ocrConfidence >= 0.5) {
            if (!normalizePan(ocrPanNumber).equals(pan)) mismatches.add("panNumber");
            if (!barCouncilValidationService.isNameMatching(ocrFields.get("name"), qrData.get("name"))) {
                mismatches.add("name");
            }
            if (!barCouncilValidationService.isNameMatching(ocrFields.get("fatherName"), qrData.get("fatherName"))) {
                mismatches.add("fatherName");
            }
            if (!datesMatch(ocrFields.get("dob"), qrData.get("dob"))) mismatches.add("dob");
        }
        List<String> unique = mismatches.stream().distinct().toList();
        if (unique.isEmpty()) {
            return new PanEvaluation(VerificationStatus.VERIFIED, List.of(), null, false);
        }
        return new PanEvaluation(VerificationStatus.PENDING, unique,
                "PAN QR fields do not match the verified Aadhaar QR or printed PAN fields: "
                        + String.join(", ", unique) + ".", false);
    }

    private Map<String, String> panQrFields(Map<String, String> data) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "panType", data.get("panType"));
        putIfPresent(values, "name", data.get("name"));
        putIfPresent(values, "fatherName", data.get("fatherName"));
        putIfPresent(values, "dob", data.get("dob"));
        putIfPresent(values, "dateOfIncorporation", data.get("dateOfIncorporation"));
        return values;
    }

    private void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }

    private String normalizePan(String value) {
        return value == null ? "" : value.replaceAll("[\\s-]", "").toUpperCase();
    }

    /**
     * Resolves the verified Aadhaar signed QR, which is the trust anchor PAN is matched against.
     */
    private QrData verifiedAadhaarQr(String userId) {
        return documentRepository.findByUserIdAndDocumentType(userId, DocumentType.AADHAAR)
                .filter(doc -> doc.getVerificationStatus() == VerificationStatus.VERIFIED)
                .filter(doc -> Boolean.TRUE.equals(doc.getIsQrVerified()))
                .filter(doc -> !Boolean.TRUE.equals(doc.getQrPrintedMismatch()))
                .map(UserDocument::getQrData)
                .orElse(null);
    }

    private boolean datesMatch(String first, String second) {
        LocalDate a = parseDate(first);
        LocalDate b = parseDate(second);
        return a != null && b != null && a.equals(b);
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (String pattern : new String[]{"d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "uuuu-M-d"}) {
            try {
                return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // try the next supported date format
            }
        }
        return null;
    }

    /** Manual review is only for conflicts a retry cannot fix. */
    private String panPendingReason(PanEvaluation evaluation) {
        if (evaluation == null || evaluation.status() != VerificationStatus.PENDING) return null;
        if (evaluation.duplicate()) return "PAN_DUPLICATE";
        if (!evaluation.mismatches().isEmpty()) return "PAN_MISMATCH";
        return "PAN_WAITING_AADHAAR";
    }

    private boolean panRequiresManualReview(PanEvaluation evaluation) {
        String reason = panPendingReason(evaluation);
        return "PAN_DUPLICATE".equals(reason) || "PAN_MISMATCH".equals(reason);
    }

    private String panReviewReason(PanEvaluation evaluation) {
        return "PAN_WAITING_AADHAAR".equals(panPendingReason(evaluation))
                ? "PAN QR verified. Verify your Aadhaar to complete PAN verification automatically."
                : evaluation.manualReason();
    }

    /** A PAN submitted before Aadhaar becomes eligible once the signed Aadhaar QR is verified. */
    private void reevaluatePendingPan(String userId) {
        Optional<UserDocument> panOpt = documentRepository.findByUserIdAndDocumentType(userId, DocumentType.PAN);
        if (panOpt.isEmpty()) return;
        UserDocument pan = panOpt.get();
        if (pan.getVerificationStatus() == VerificationStatus.VERIFIED
                || pan.getVerificationStatus() == VerificationStatus.REJECTED) return;
        PanEvaluation evaluation;
        if (pan.getPanQrData() != null) {
            evaluation = evaluatePanWithQr(userId, null, null, 0, pan.getPanQrData());
            String qrPan = pan.getPanQrData().get("pan");
            pan.setPanNumber(qrPan);
            pan.setPanNumberHash(hashPan(qrPan));
            if (qrPan != null) pan.setMaskedDocumentNumber(ocrExtractionService.maskPan(qrPan));
            pan.setExtractedData(panQrFields(pan.getPanQrData()));
        } else {
            // Manual submissions (no signed QR) never auto-verify, even after Aadhaar.
            return;
        }
        pan.setVerificationStatus(evaluation.status());
        pan.setCrossDocumentMismatchFields(evaluation.mismatches().isEmpty()
                ? null : evaluation.mismatches());
        pan.setRequiresManualReview(panRequiresManualReview(evaluation));
        pan.setPendingReason(panPendingReason(evaluation));
        pan.setManualReviewReason(panReviewReason(evaluation));
        pan.setAutoApproved(evaluation.status() == VerificationStatus.VERIFIED);
        pan.setVerifiedAt(evaluation.status() == VerificationStatus.VERIFIED ? Instant.now() : null);
        pan.setFailureReason(evaluation.status() == VerificationStatus.VERIFIED
                ? null : evaluation.manualReason());
        pan.setUpdatedAt(Instant.now());
        UserDocument savedPan = documentRepository.save(pan);
        applyDocumentVerificationToLawyer(userId, savedPan);
        log.info("PAN re-evaluated after Aadhaar verification: status={}", evaluation.status());
    }

    // ── Manual review ───────────────────────────────────────────────────

    /** Dedicated QR-only scan, useful when the printed PAN fields are hard to OCR. */
    public UserDocument scanPanQr(String userId, MultipartFile qrImage) {
        validateExistingLawyer(userId);
        PanQrAttempt attempt = readPanQr(qrImage, userId);
        if (attempt.scan() == null) {
            throw new DocumentVerificationException("PAN QR was not usable: "
                    + (attempt.failureReason() == null || attempt.failureReason().isBlank()
                        ? "no readable QR was found." : attempt.failureReason()));
        }
        return applyPanQrScan(userId, attempt.scan());
    }

    /** Live scanner sends the QR string directly; no image or QR detection involved. */
    public UserDocument scanPanQrText(String userId, String qrText) {
        validateExistingLawyer(userId);
        if (qrText == null || qrText.isBlank()) {
            throw new DocumentVerificationException("The scanned PAN QR is empty.");
        }
        VisionVerificationClient.PanQrResult qr = visionVerificationClient.verifyPanQrText(qrText.trim());
        if (qr == null || !Boolean.TRUE.equals(qr.verified())
                || !Boolean.TRUE.equals(qr.signatureVerified())
                || qr.data() == null || qr.data().isEmpty()) {
            String reason = qr == null || qr.reason() == null || qr.reason().isBlank()
                    ? "PAN Secure QR was not found or its signature could not be verified."
                    : qr.reason();
            throw new DocumentVerificationException("PAN QR was not usable: " + reason);
        }
        return applyPanQrScan(userId, toPanQrScan(qr, userId));
    }

    private UserDocument applyPanQrScan(String userId, PanQrScan scan) {
        PanEvaluation evaluation = evaluatePanWithQr(userId, null, null, 0, scan.data());
        UserDocument doc = documentRepository.findByUserIdAndDocumentType(userId, DocumentType.PAN)
                .orElse(new UserDocument());
        String previousPhoto = doc.getPanQrPhotoPublicId();
        String pan = normalizePan(scan.data().get("pan"));
        doc.setUserId(userId);
        doc.setDocumentType(DocumentType.PAN);
        doc.setVerificationStatus(evaluation.status());
        doc.setExtractedData(panQrFields(scan.data()));
        doc.setPanNumber(pan);
        doc.setPanNumberHash(hashPan(pan));
        if (pan != null) doc.setMaskedDocumentNumber(ocrExtractionService.maskPan(pan));
        doc.setPanQrData(scan.data());
        doc.setPanQrRawText(scan.rawText());
        doc.setIsPanQrVerified(true);
        doc.setPanQrVersion(scan.version());
        // Keep the QR photo for a verified PAN or while it is waiting for Aadhaar.
        boolean waitingForAadhaar = evaluation.status() == VerificationStatus.PENDING
                && !evaluation.duplicate()
                && evaluation.mismatches().isEmpty();
        boolean keepQrPhoto = evaluation.status() == VerificationStatus.VERIFIED
                || waitingForAadhaar;
        doc.setPanQrPhotoUrl(keepQrPhoto ? scan.photoUrl() : null);
        doc.setPanQrPhotoPublicId(keepQrPhoto ? scan.photoPublicId() : null);
        doc.setCrossDocumentMismatchFields(evaluation.mismatches().isEmpty()
                ? null : evaluation.mismatches());
        doc.setRequiresManualReview(panRequiresManualReview(evaluation));
        doc.setPendingReason(panPendingReason(evaluation));
        doc.setManualReviewReason(panReviewReason(evaluation));
        doc.setAutoApproved(evaluation.status() == VerificationStatus.VERIFIED);
        doc.setFailureReason(evaluation.status() == VerificationStatus.VERIFIED
                ? null : evaluation.manualReason());
        doc.setFailureFile(null);
        doc.setVerifiedAt(evaluation.status() == VerificationStatus.VERIFIED ? Instant.now() : null);
        doc.setReviewedBy(null);
        doc.setReviewedAt(null);
        doc.setReviewDecision(null);
        doc.setReviewNotes(null);
        doc.setConfidenceScore(1.0);
        doc.setUpdatedAt(Instant.now());
        UserDocument saved = documentRepository.save(doc);
        if (!keepQrPhoto && scan.photoPublicId() != null) {
            try {
                cloudinaryService.delete(scan.photoPublicId());
            } catch (Exception error) {
                log.warn("Could not delete unused PAN QR photo: {}", error.getClass().getSimpleName());
            }
        }
        if (previousPhoto != null && (scan.photoPublicId() == null
                || !previousPhoto.equals(scan.photoPublicId()))) {
            try {
                cloudinaryService.delete(previousPhoto);
            } catch (Exception error) {
                log.warn("Could not delete replaced PAN QR photo: {}", error.getClass().getSimpleName());
            }
        }
        try {
            applyDocumentVerificationToLawyer(userId, saved);
        } catch (Exception error) {
            // The document itself is already saved and usable; do not fail the QR response
            // because a profile refresh failed.
            log.warn("PAN QR saved but lawyer profile refresh failed: {}", error.getClass().getSimpleName());
        }
        return saved;
    }

    public UserDocument approveDocument(String documentId, String adminId, String notes) {
        UserDocument doc = requireReviewableDocument(documentId);
        if (doc.getDocumentType() == DocumentType.AADHAAR
                && !Boolean.TRUE.equals(doc.getIsQrVerified())) {
            throw new DocumentVerificationException(
                    "Aadhaar cannot be approved without a verified Secure QR. "
                    + "Ask the applicant to upload e-Aadhaar or retry with a clear photo.");
        }
        if (doc.getDocumentType() == DocumentType.AADHAAR && doc.getQrData() != null) {
            // Review resolves the printed-vs-QR mismatch in favour of the signed QR, so
            // PAN and selfie verification keep working after approval.
            QrData qr = doc.getQrData();
            Map<String, String> fields = new LinkedHashMap<>();
            putIfPresent(fields, "name", qr.getName());
            putIfPresent(fields, "dob", qr.getDob());
            String gender = switch (Objects.toString(qr.getGender(), "")) {
                case "M" -> "MALE";
                case "F" -> "FEMALE";
                case "T" -> "TRANSGENDER";
                default -> qr.getGender();
            };
            putIfPresent(fields, "gender", gender);
            putIfPresent(fields, "address", qr.getAddress());
            putIfPresent(fields, "pincode", qr.getPincode());
            doc.setExtractedData(fields);
            doc.setQrPrintedMismatch(false);
            doc.setQrMismatchFields(null);
        }
        doc.setVerificationStatus(VerificationStatus.VERIFIED);
        doc.setVerifiedAt(Instant.now());
        doc.setRequiresManualReview(false);
        doc.setPendingReason(null);
        doc.setManualReviewReason(null);
        doc.setAutoApproved(false);
        doc.setFailureReason(null);
        doc.setFailureFile(null);
        doc.setReviewedBy(adminId);
        doc.setReviewedAt(Instant.now());
        doc.setReviewDecision("APPROVED");
        doc.setReviewNotes(notes);
        doc.setUpdatedAt(Instant.now());
        UserDocument saved = documentRepository.save(doc);
        applyDocumentVerificationToLawyer(doc.getUserId(), saved);
        return saved;
    }

    public UserDocument rejectDocument(String documentId, String adminId, String reason) {
        UserDocument doc = requireReviewableDocument(documentId);
        String rejection = reason == null || reason.isBlank()
                ? "Rejected by admin review." : reason.trim();
        doc.setVerificationStatus(VerificationStatus.REJECTED);
        doc.setVerifiedAt(null);
        doc.setRequiresManualReview(false);
        doc.setPendingReason(null);
        doc.setAutoApproved(false);
        doc.setFailureReason(rejection);
        doc.setFailureFile(null);
        doc.setReviewedBy(adminId);
        doc.setReviewedAt(Instant.now());
        doc.setReviewDecision("REJECTED");
        doc.setReviewNotes(rejection);
        doc.setUpdatedAt(Instant.now());
        UserDocument saved = documentRepository.save(doc);
        applyDocumentVerificationToLawyer(doc.getUserId(), saved);
        return saved;
    }

    private UserDocument requireReviewableDocument(String documentId) {
        UserDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentVerificationException("Document not found."));
        if (doc.getDocumentType() != DocumentType.PAN && doc.getDocumentType() != DocumentType.AADHAAR) {
            throw new DocumentVerificationException(
                    "Manual review is available for Aadhaar and PAN documents only.");
        }
        if (doc.getRequiresManualReview() == null || !doc.getRequiresManualReview()) {
            throw new DocumentVerificationException("Document is not waiting for manual review.");
        }
        return doc;
    }

    private boolean hasVerifiedSelfie(String userId, UserDocument aadhaar) {
        return isVerifiedAadhaar(aadhaar) && aadhaar.getId() != null && aadhaar.getSubmissionToken() != null
                && selfieVerificationRepository.existsByUserIdAndAadhaarDocumentIdAndAadhaarSubmissionTokenAndVerificationStatus(
                        userId, aadhaar.getId(), aadhaar.getSubmissionToken(), VerificationStatus.VERIFIED);
    }

    public boolean areAllMandatoryDocumentsVerified(String userId) {
        return hasAllRequiredDocuments(userId, documentRepository.findByUserId(userId));
    }

    public void refreshLawyerStatus(String userId) {
        boolean complete = areAllMandatoryDocumentsVerified(userId);
        findExperiencedLawyer(userId).ifPresent(profile -> {
            profile.setVerificationStatus(complete ? Verification_Status.VERIFIED : Verification_Status.PENDING);
            profile.setVerifiedAt(complete ? System.currentTimeMillis() : null);
            lawyerExperiencedProfileRepository.save(profile);
        });
        findFresherLawyer(userId).ifPresent(profile -> {
            profile.setVerificationStatus(complete ? Verification_Status.VERIFIED : Verification_Status.PENDING);
            profile.setVerifiedAt(complete ? System.currentTimeMillis() : null);
            lawyerFresherProfileRepository.save(profile);
        });
    }

    private void validateRequest(String userId, DocumentType documentType,
                                  MultipartFile frontImage, MultipartFile backImage) {
        validateDocumentOwner(userId);

        if (documentType == null) {
            throw new DocumentVerificationException(
                    "Document type is required. Supported types: AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE.");
        }

        if (frontImage == null || frontImage.isEmpty()) {
            throw new DocumentVerificationException("Front image of the document is required.",
                    null, safeFileName(frontImage));
        }

        if (documentType == DocumentType.AADHAAR && (backImage == null || backImage.isEmpty())) {
            throw new DocumentVerificationException(
                    "Both front and back images are required for Aadhaar card verification.",
                    null, safeFileName(backImage));
        }
        if (documentType == DocumentType.PAN && backImage != null && !backImage.isEmpty()) {
            throw new DocumentVerificationException("PAN verification requires only the front image.",
                    null, safeFileName(backImage));
        }
    }

    private void validateDocumentOwner(String userId) {
        validateExistingLawyer(userId);
    }

    private AadhaarQrScan readAadhaarQr(MultipartFile backImage, MultipartFile frontImage,
                                        Map<String, String> printed, String maskedNumber) {
        AadhaarQrScan fromBack = backImage != null && !backImage.isEmpty()
                ? decodeQr(backImage, "back", printed, maskedNumber) : null;
        if (fromBack != null && fromBack.verified()) return fromBack;
        AadhaarQrScan fromFront = decodeQr(frontImage, "front", printed, maskedNumber);
        if (fromFront != null && fromFront.verified()) return fromFront;
        return fromBack != null ? fromBack : fromFront;
    }

    private AadhaarQrScan decodeQr(MultipartFile image, String side,
                                   Map<String, String> printed, String maskedNumber) {
        if (image == null || image.isEmpty()) return null;
        try {
            VisionVerificationClient.QrResult qr = visionVerificationClient.verifyAadhaarQr(
                    image.getBytes(), printed, maskedNumber == null ? "" : maskedNumber);
            List<String> mismatches = qr == null || qr.mismatchFields() == null
                    ? List.of() : List.copyOf(qr.mismatchFields());
            if (qr == null || !Boolean.TRUE.equals(qr.verified()) || !Boolean.TRUE.equals(qr.photoAvailable())
                    || qr.data() == null || qr.data().isEmpty()) {
                String reason = qr == null || qr.reason() == null || qr.reason().isBlank()
                        ? "Aadhaar Secure QR could not be verified." : qr.reason();
                return new AadhaarQrScan(null, false, false, List.of(), side, reason);
            }
            QrData data = new QrData();
            data.setAadhaarLast4(qr.data().get("aadhaarLast4"));
            data.setName(qr.data().get("name"));
            data.setDob(qr.data().get("dob"));
            data.setGender(qr.data().get("gender"));
            data.setAddress(qr.data().get("address"));
            data.setPincode(qr.data().get("pincode"));
            data.setImageSide(side);
            data.setCertificate(qr.certificate());
            data.setVersion(qr.version());
            data.setRawText(qr.rawText());
            data.setScannedAt(Instant.now());
            boolean mismatch = Boolean.TRUE.equals(qr.printedMismatch()) || !mismatches.isEmpty();
            return new AadhaarQrScan(data, true, mismatch, mismatches, side, qr.reason());
        } catch (Exception error) {
            log.warn("Aadhaar QR scan failed for {} side: {}", side, error.getClass().getSimpleName());
            return new AadhaarQrScan(null, false, false, List.of(), side,
                    "Aadhaar Secure QR scan failed; upload a clear photo of the QR side.");
        }
    }

    /** QR text is the readable demographic data extracted from the signed UIDAI QR. */
    private String qrText(QrData data) {
        Map<String, String> values = qrDecodedData(data);
        if (values == null) return null;
        try {
            return JSON.writeValueAsString(values);
        } catch (Exception error) {
            log.warn("Could not serialize Aadhaar QR data: {}", error.getClass().getSimpleName());
            return values.toString();
        }
    }

    private Map<String, String> qrDecodedData(QrData data) {
        if (data == null) return null;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("aadhaarLast4", data.getAadhaarLast4());
        values.put("name", data.getName());
        values.put("dob", data.getDob());
        values.put("gender", data.getGender());
        values.put("address", data.getAddress());
        values.put("pincode", data.getPincode());
        values.values().removeIf(Objects::isNull);
        return values;
    }

    private record AadhaarQrScan(QrData data, boolean verified, boolean printedMismatch,
                                 List<String> mismatchFields, String side, String reason) {}

    // ── Mapping ─────────────────────────────────────────────────────────

    private DocumentVerificationResponse toResponse(UserDocument doc) {
        return toResponse(doc, null);
    }

    private DocumentVerificationResponse toResponse(UserDocument doc, LawyerUpdateResult lawyerUpdate) {
        String lawyerStatusStr = lawyerUpdate != null && lawyerUpdate.lawyerStatus() != null
                ? lawyerUpdate.lawyerStatus().name()
                : null;
        Boolean nameMatched = lawyerUpdate != null ? lawyerUpdate.nameMatched() : null;
        String message = lawyerUpdate != null ? lawyerUpdate.message() : null;

        DocumentVerificationResponse response = DocumentVerificationResponse.from(
                doc.getId(),
                doc.getDocumentType(),
                doc.getVerificationStatus(),
                doc.getMaskedDocumentNumber(),
                doc.getExtractedData(),
                doc.getOcrConfidence(),
                doc.getFailureReason(),
                doc.getCreatedAt(),
                lawyerStatusStr,
                nameMatched,
                message
        );
        response.setFile(doc.getFailureFile());
        response.setQrDecodedData(doc.getQrDecodedData());
        response.setIsQrVerified(doc.getIsQrVerified());
        response.setQrImageSide(doc.getQrImageSide());
        response.setQrPrintedMismatch(doc.getQrPrintedMismatch());
        response.setQrMismatchFields(doc.getQrMismatchFields());
        response.setConfidenceScore(doc.getConfidenceScore());
        response.setAutoApproved(doc.getAutoApproved());
        response.setRequiresManualReview(doc.getRequiresManualReview());
        response.setManualReviewReason(doc.getManualReviewReason());
        response.setVerifiedAt(doc.getVerifiedAt());
        return response;
    }
}
