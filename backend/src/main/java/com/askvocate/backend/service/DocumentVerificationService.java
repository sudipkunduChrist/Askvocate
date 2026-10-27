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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.regex.Matcher;
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

    private final S3StorageService storageService;
    private final OcrExtractionService ocrExtractionService;
    private final BarCouncilValidationService barCouncilValidationService;
    private final DegreeCertificatePolicy degreeCertificatePolicy;
    private final UserDocumentRepository documentRepository;
    private final LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository;
    private final LawyerFresherProfileRepository lawyerFresherProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final VisionVerificationClient visionVerificationClient;
    private final SelfieVerificationRepository selfieVerificationRepository;
    private final String aadhaarHashKey;

    @Autowired(required = false)
    private AdvocateVerificationApiClient advocateVerificationApiClient;

    public record GoldenIdentity(String name, String dob, String fatherName, String gender, String source) {}

    public void setAdvocateVerificationApiClient(AdvocateVerificationApiClient advocateVerificationApiClient) {
        this.advocateVerificationApiClient = advocateVerificationApiClient;
    }

    public Optional<GoldenIdentity> findGoldenIdentity(String userId) {
        try {
            Optional<UserDocument> aadhaarOpt = documentRepository.findByUserIdAndDocumentType(userId, DocumentType.AADHAAR);
            if (aadhaarOpt != null && aadhaarOpt.isPresent()) {
                UserDocument aadhaar = aadhaarOpt.get();
                if (aadhaar.getVerificationStatus() == VerificationStatus.VERIFIED) {
                    if (aadhaar.getQrData() != null && aadhaar.getQrData().getName() != null) {
                        QrData qr = aadhaar.getQrData();
                        return Optional.of(new GoldenIdentity(qr.getName(), qr.getDob(), null, qr.getGender(), "AADHAAR"));
                    }
                    if (aadhaar.getExtractedData() != null && aadhaar.getExtractedData().get("name") != null) {
                        Map<String, String> data = aadhaar.getExtractedData();
                        return Optional.of(new GoldenIdentity(data.get("name"), data.get("dob"), null, data.get("gender"), "AADHAAR"));
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            Optional<UserDocument> panOpt = documentRepository.findByUserIdAndDocumentType(userId, DocumentType.PAN);
            if (panOpt != null && panOpt.isPresent()) {
                UserDocument pan = panOpt.get();
                if (pan.getVerificationStatus() == VerificationStatus.VERIFIED) {
                    Map<String, String> data = pan.getPanQrData() != null && !pan.getPanQrData().isEmpty()
                            ? pan.getPanQrData() : pan.getExtractedData();
                    if (data != null && data.get("name") != null && !data.get("name").isBlank()) {
                        return Optional.of(new GoldenIdentity(data.get("name"), data.get("dob"), data.get("fatherName"), null, "PAN"));
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            List<UserDocument> allDocs = documentRepository.findByUserId(userId);
            if (allDocs != null) {
                for (UserDocument doc : allDocs) {
                    if (doc.getDocumentType() == DocumentType.AADHAAR && doc.getVerificationStatus() == VerificationStatus.VERIFIED) {
                        if (doc.getQrData() != null && doc.getQrData().getName() != null) {
                            QrData qr = doc.getQrData();
                            return Optional.of(new GoldenIdentity(qr.getName(), qr.getDob(), null, qr.getGender(), "AADHAAR"));
                        }
                        if (doc.getExtractedData() != null && doc.getExtractedData().get("name") != null) {
                            Map<String, String> data = doc.getExtractedData();
                            return Optional.of(new GoldenIdentity(data.get("name"), data.get("dob"), null, data.get("gender"), "AADHAAR"));
                        }
                    }
                }
                for (UserDocument doc : allDocs) {
                    if (doc.getDocumentType() == DocumentType.PAN && doc.getVerificationStatus() == VerificationStatus.VERIFIED) {
                        Map<String, String> data = doc.getPanQrData() != null && !doc.getPanQrData().isEmpty()
                                ? doc.getPanQrData() : doc.getExtractedData();
                        if (data != null && data.get("name") != null && !data.get("name").isBlank()) {
                            return Optional.of(new GoldenIdentity(data.get("name"), data.get("dob"), data.get("fatherName"), null, "PAN"));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return Optional.empty();
    }

    public DocumentVerificationService(S3StorageService storageService,
                                       OcrExtractionService ocrExtractionService,
                                       BarCouncilValidationService barCouncilValidationService,
                                       DegreeCertificatePolicy degreeCertificatePolicy,
                                       UserDocumentRepository documentRepository,
                                       LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository,
                                       LawyerFresherProfileRepository lawyerFresherProfileRepository,
                                       ClientProfileRepository clientProfileRepository,
                                       VisionVerificationClient visionVerificationClient,
                                       SelfieVerificationRepository selfieVerificationRepository,
                                       @Value("${verification.aadhaar-hash-key:}") String aadhaarHashKey) {
        this.storageService = storageService;
        this.ocrExtractionService = ocrExtractionService;
        this.barCouncilValidationService = barCouncilValidationService;
        this.degreeCertificatePolicy = degreeCertificatePolicy;
        this.documentRepository = documentRepository;
        this.lawyerExperiencedProfileRepository = lawyerExperiencedProfileRepository;
        this.lawyerFresherProfileRepository = lawyerFresherProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.visionVerificationClient = visionVerificationClient;
        this.selfieVerificationRepository = selfieVerificationRepository;
        this.aadhaarHashKey = aadhaarHashKey;
        if (aadhaarHashKey == null || aadhaarHashKey.isBlank()) {
            log.warn("AADHAAR_HASH_KEY is not set: duplicate identity/enrollment detection is disabled.");
        }
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

        String folder = "documents/" + documentType.name();
        List<StorageRef> storageRefs = new ArrayList<>();
        OcrExtractionService.ExtractionResult extractionResult;
        StringBuilder degreeOcrText = new StringBuilder();
        Object degreeRawOcrData = null;
        String failureFile = null;
        String currentFile = safeFileName(frontImage);

        try {
            // 2. Upload front image with OCR
            log.info("Uploading front image for documentType={}, bytes={}", documentType, frontImage.getSize());
            S3StorageService.UploadResult frontResult = uploadWithOcr(frontImage, folder, "front");
            storageRefs.add(frontResult.storageRef());
            if (documentType == DocumentType.DEGREE_CERTIFICATE) {
                degreeOcrText.append(ocrText(frontResult.rawOcrData())).append('\n');
            }
            if (documentType != DocumentType.AADHAAR && documentType != DocumentType.PAN) {
                degreeRawOcrData = frontResult.rawOcrData();
            }

            // 3. Extract OCR from front
            OcrExtractionService.ExtractionResult frontExtraction =
                    ocrExtractionService.extract(frontResult.rawOcrData(), documentType, "front");
            if (!frontExtraction.success()) failureFile = currentFile;

            // 4. Upload and extract back image if provided
            if (backImage != null && !backImage.isEmpty()) {
                currentFile = safeFileName(backImage);
                log.info("Uploading back image for documentType={}, bytes={}", documentType, backImage.getSize());
                S3StorageService.UploadResult backResult = uploadWithOcr(backImage, folder, "back");
                storageRefs.add(backResult.storageRef());
                if (documentType == DocumentType.DEGREE_CERTIFICATE) {
                    degreeOcrText.append(ocrText(backResult.rawOcrData())).append('\n');
                }

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
                cleanupStorageAssets(storageRefs);
            } catch (Exception cleanupException) {
                log.warn("Failed to clean up S3 assets after verification failure", cleanupException);
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
            // Bar Council, COP, Degree and AIBE verification logic is evaluated below
            // using the Golden Identity (Aadhaar/PAN) cross-matching and local OCR.
            status = VerificationStatus.PENDING;
        } else if (qrVerified) {
            // The signed UIDAI QR is the authority; printed OCR is no longer a gate.
            status = VerificationStatus.VERIFIED;
        } else {
            // QR side unreadable: ask for an e-Aadhaar or a clearer QR photo.
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
        if (documentType == DocumentType.AADHAAR && status == VerificationStatus.PENDING) {
            String qrReason = qrScan != null && qrScan.reason() != null && !qrScan.reason().isBlank()
                    ? qrScan.reason()
                    : "Aadhaar Secure QR could not be verified; upload a clear photo of the QR side.";
            manualReviewReason = "Aadhaar QR could not be read. Upload a clear photo of the QR side, "
                    + "or your free e-Aadhaar from uidai.gov.in. " + qrReason;
            if (!ocrSuccess && extractionResult.error() != null) {
                manualReviewReason += " " + extractionResult.error();
            }
        }

        // Certificate-backed documents: compare the printed name with the profile / Golden Identity and
        // detect a duplicate Bar Council enrollment number.
        boolean isBarDocument = documentType == DocumentType.BAR_COUNCIL_ID
                || documentType == DocumentType.BAR_CERTIFICATE
                || documentType == DocumentType.CERTIFICATE_OF_PRACTICE;
        Optional<GoldenIdentity> goldenIdentityOpt = findGoldenIdentity(userId);
        boolean identityVerified = goldenIdentityOpt.isPresent();
        boolean certificateDocument = documentType == DocumentType.DEGREE_CERTIFICATE
                || documentType == DocumentType.BAR_CERTIFICATE
                || documentType == DocumentType.BAR_COUNCIL_ID
                || documentType == DocumentType.CERTIFICATE_OF_PRACTICE
                || documentType == DocumentType.AIBE_CERTIFICATE;
        if (certificateDocument && identityVerified && degreeRawOcrData != null) {
            String matchedName = ocrExtractionService.bestNameFor(
                    degreeRawOcrData, goldenIdentityOpt.get().name());
            if (matchedName != null) {
                Map<String, String> fields = new LinkedHashMap<>(extractionResult.extractedFields());
                fields.put("name", matchedName);
                extractionResult = new OcrExtractionService.ExtractionResult(
                        true, fields, extractionResult.maskedDocumentNumber(),
                        extractionResult.confidence(), null,
                        extractionResult.aadhaarNumber(), extractionResult.panNumber());
            }
        }
        String extractedName = extractionResult.extractedFields().get("name");
        String profileName = lawyerProfileName(userId);
        String verifiedName = identityVerified ? goldenIdentityOpt.get().name() : profileName;

        boolean certificateNameMismatch = extractedName != null && verifiedName != null
                && !barCouncilValidationService.isNameMatching(extractedName, verifiedName);
        DegreeCertificatePolicy.Decision degreeDecision = null;
        DegreeCertificatePolicy.DegreeEvidence degreeEvidence = null;
        if (documentType == DocumentType.DEGREE_CERTIFICATE) {
            var fields = extractionResult.extractedFields();
            degreeEvidence = new DegreeCertificatePolicy.DegreeEvidence(
                    fields.get("institution"), fields.get("college"), fields.get("degreeType"),
                    fields.get("courseDuration"), fields.get("year"), fields.get("passingYear"),
                    fields.get("issueDate"));
            degreeDecision = degreeCertificatePolicy.evaluate(degreeOcrText.toString(), extractedName,
                    identityVerified ? goldenIdentityOpt.get().name() : null,
                    extractionResult.confidence(), degreeEvidence);
        }
        String enrollmentNumber = extractionResult.extractedFields().get("enrollmentNumber");
        String enrollmentHash = null;
        boolean duplicateEnrollment = false;
        AdvocateVerificationApiClient.AdvocateVerificationResult externalBarCheck = null;
        if (isBarDocument && enrollmentNumber != null && !enrollmentNumber.isBlank()) {
            enrollmentHash = hashEnrollment(enrollmentNumber);
            Optional<UserDocument> duplicate = enrollmentHash == null ? Optional.empty()
                    : documentRepository.findByEnrollmentNumberHash(enrollmentHash);
            duplicateEnrollment = duplicate.isPresent()
                    && !Objects.equals(userId, duplicate.get().getUserId());

            if (advocateVerificationApiClient != null) {
                try {
                    externalBarCheck = advocateVerificationApiClient.verifyAdvocate(enrollmentNumber, verifiedName);
                    if (externalBarCheck.verified()) {
                        extractionResult.extractedFields().put("licenseStatus", externalBarCheck.licenseStatus());
                        if (externalBarCheck.enrollmentDate() != null) {
                            extractionResult.extractedFields().put("enrollmentDate", externalBarCheck.enrollmentDate());
                        }
                    }
                } catch (Exception e) {
                    log.warn("External Bar Council verification check skipped: {}", e.getMessage());
                }
            }
        }

        // Determine verification status:
        // CASE 0: Degree policy rejected the certificate outright.
        // CASE 1: Identity (Aadhaar or PAN) is ALREADY verified -> Auto-approve clean certificates!
        // CASE 2: Identity is NOT yet verified -> Mark AWAITING_IDENTITY (will auto-approve retroactively)
        if (documentType != DocumentType.AADHAAR && documentType != DocumentType.PAN) {
            if (degreeDecision != null && degreeDecision.rejected()) {
                status = VerificationStatus.REJECTED;
            } else if (identityVerified) {
                if (isBarDocument) {
                    if (ocrSuccess && enrollmentNumber != null && !duplicateEnrollment && !certificateNameMismatch) {
                        status = VerificationStatus.VERIFIED;
                    } else {
                        status = VerificationStatus.PENDING;
                    }
                } else if (documentType == DocumentType.DEGREE_CERTIFICATE || documentType == DocumentType.AIBE_CERTIFICATE) {
                    if (degreeDecision != null && degreeDecision.manualReview()) {
                        status = VerificationStatus.PENDING;
                    } else if (ocrSuccess && !certificateNameMismatch) {
                        status = VerificationStatus.VERIFIED;
                    } else {
                        status = VerificationStatus.PENDING;
                    }
                } else {
                    status = VerificationStatus.PENDING;
                }
            } else {
                status = VerificationStatus.PENDING;
            }
        }

        // Manual review is reserved for data conflicts that a retry cannot fix.
        // Every other pending outcome is a re-upload state and auto-approves on a correct retry.
        String pendingReason = null;
        boolean requiresManualReview = false;
        if (status == VerificationStatus.PENDING) {
            if (documentType == DocumentType.AADHAAR) {
                // QR verified is VERIFIED; only an unreadable QR remains pending.
                pendingReason = "AADHAAR_QR_UNREADABLE";
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
            } else {
                String prefix = switch (documentType) {
                    case BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE -> "BAR";
                    case DEGREE_CERTIFICATE -> "DEGREE";
                    case AIBE_CERTIFICATE -> "AIBE";
                    default -> "DOC";
                };
                if (degreeDecision != null && degreeDecision.manualReview()) {
                    pendingReason = degreeDecision.reasonCode();
                    requiresManualReview = true;
                } else if (!ocrSuccess) {
                    if (documentType == DocumentType.DEGREE_CERTIFICATE) {
                        pendingReason = "DEGREE_OCR_INCOMPLETE";
                        requiresManualReview = true;
                    } else {
                        pendingReason = prefix + "_OCR_UNREADABLE";
                    }
                } else if (duplicateEnrollment) {
                    pendingReason = "BAR_DUPLICATE";
                    requiresManualReview = true;
                } else if (!identityVerified) {
                    pendingReason = "AWAITING_IDENTITY";
                    requiresManualReview = false;
                } else if (certificateNameMismatch) {
                    pendingReason = prefix + "_MISMATCH";
                    requiresManualReview = true;
                } else {
                    pendingReason = prefix + "_REVIEW";
                    requiresManualReview = true;
                }
            }
        }
        if (degreeDecision != null && degreeDecision.rejected()) {
            pendingReason = degreeDecision.reasonCode();
            manualReviewReason = degreeDecision.userMessage();
        }
        if ("PAN_WAITING_AADHAAR".equals(pendingReason)) {
            manualReviewReason = "PAN QR verified. Verify your Aadhaar to complete PAN verification automatically.";
        }
        if (status == VerificationStatus.PENDING && documentType != DocumentType.AADHAAR
                && documentType != DocumentType.PAN) {
            if (degreeDecision != null && degreeDecision.manualReview()) {
                manualReviewReason = degreeDecision.userMessage();
            } else {
                manualReviewReason = switch (pendingReason == null ? "" : pendingReason) {
                    case "AWAITING_IDENTITY" ->
                            "Certificate uploaded and validated. Verify your Aadhaar or PAN card to complete verification automatically.";
                    case "BAR_OCR_UNREADABLE", "DEGREE_OCR_UNREADABLE", "AIBE_OCR_UNREADABLE" ->
                            "Invalid '" + documentTypeLabel(documentType) + "'. Please upload again for verification.";
                    case "DEGREE_OCR_INCOMPLETE" ->
                            "We could read this certificate but not the holder's name. Sent for manual review.";
                    case "BAR_DUPLICATE" ->
                            "This Bar Council enrollment number is already registered to another account. Manual review is in progress.";
                    case "BAR_MISMATCH", "DEGREE_MISMATCH", "AIBE_MISMATCH" ->
                            "The content on '" + documentTypeLabel(documentType) + "' does not match your verified profile. Sent for manual review.";
                    default -> "'" + documentTypeLabel(documentType) + "' is blurry or unclear. Sent for manual review.";
                };
            }
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
            log.info("Cleaning temporary S3 assets: type={}, status={}", documentType, status);
            cleanupStorageAssets(storageRefs);
            storageRefs = new ArrayList<>();
        }

        // 6. Look up existing document record or create a new one (supports re-verification)
        UserDocument userDocument = documentRepository.findByUserIdAndDocumentType(userId, documentType)
                .orElse(new UserDocument());
        String previousQrPhoto = userDocument.getQrData() == null
                ? null : userDocument.getQrData().getPhotoPublicId();
        String previousPanQrPhoto = userDocument.getPanQrPhotoPublicId();
        List<StorageRef> previousRefs = userDocument.getStorageReferences() == null
                ? List.of() : new ArrayList<>(userDocument.getStorageReferences());

        userDocument.setUserId(userId);
        userDocument.setDocumentType(documentType);
        if (documentType == DocumentType.AADHAAR) userDocument.setSubmissionToken(UUID.randomUUID().toString());
        userDocument.setVerificationStatus(status);
        Map<String, String> panQrFields = documentType == DocumentType.PAN && panQrScan != null
                ? panQrFields(panQrScan.data()) : null;
        Map<String, String> aadhaarQrFields = null;
        if (documentType == DocumentType.AADHAAR && qrData != null) {
            aadhaarQrFields = new LinkedHashMap<>();
            putIfPresent(aadhaarQrFields, "name", qrData.getName());
            putIfPresent(aadhaarQrFields, "dob", qrData.getDob());
            putIfPresent(aadhaarQrFields, "gender", fullGender(qrData.getGender()));
            putIfPresent(aadhaarQrFields, "address", qrData.getAddress());
            putIfPresent(aadhaarQrFields, "pincode", qrData.getPincode());
            putIfPresent(aadhaarQrFields, "aadhaarLast4", qrData.getAadhaarLast4());
        }
        userDocument.setExtractedData(documentType == DocumentType.PAN && panQrFields != null
                ? panQrFields
                : aadhaarQrFields != null
                    ? aadhaarQrFields
                    : documentType == DocumentType.AADHAAR || documentType == DocumentType.PAN
                        || extractionResult.success()
                        ? extractionResult.extractedFields() : Map.of());
        if (documentType == DocumentType.AADHAAR && qrData != null && qrData.getAadhaarLast4() != null) {
            userDocument.setMaskedDocumentNumber("XXXX-XXXX-" + qrData.getAadhaarLast4());
        } else {
            userDocument.setMaskedDocumentNumber(documentType == DocumentType.AADHAAR || extractionResult.success()
                    ? extractionResult.maskedDocumentNumber() : null);
        }
        userDocument.setAadhaarNumber(documentType == DocumentType.AADHAAR
                ? extractionResult.aadhaarNumber() : null);
        userDocument.setAadhaarNumberHash(documentType == DocumentType.AADHAAR
                ? hashAadhaar(extractionResult.aadhaarNumber()) : null);
        String panNumber = documentType == DocumentType.PAN
                ? (panQrScan != null ? panQrScan.data().get("pan") : extractionResult.panNumber())
                : null;
        userDocument.setPanNumber(panNumber);
        userDocument.setPanNumberHash(documentType == DocumentType.PAN ? hashPan(panNumber) : null);
        userDocument.setEnrollmentNumberHash(isBarDocument ? enrollmentHash : null);
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
                    storageService.delete(panQrScan.photoPublicId());
                } catch (Exception error) {
                    log.warn("Could not delete unused PAN QR photo: {}", error.getClass().getSimpleName());
                }
            }
        }
        List<String> mismatchFields = null;
        if (documentType == DocumentType.PAN && panEvaluation != null && !panEvaluation.mismatches().isEmpty()) {
            mismatchFields = panEvaluation.mismatches();
        } else if (certificateNameMismatch) {
            mismatchFields = List.of("name");
        }
        userDocument.setCrossDocumentMismatchFields(mismatchFields);
        userDocument.setVerificationFlags(degreeDecision != null && !degreeDecision.flags().isEmpty()
                ? degreeDecision.flags() : null);
        if (documentType == DocumentType.DEGREE_CERTIFICATE && degreeEvidence != null) {
            String institutionText = String.join("\n",
                    degreeEvidence.institution() == null ? "" : degreeEvidence.institution(),
                    degreeEvidence.college() == null ? "" : degreeEvidence.college());
            DegreeInstitutionAllowlist.Match institutionMatch = degreeCertificatePolicy.matchInstitution(
                    institutionText + "\n" + degreeOcrText);
            userDocument.setInstitutionCanonicalName(institutionMatch == null ? null : institutionMatch.name());
            userDocument.setInstitutionMatchType(institutionMatch == null ? null : institutionMatch.matchType());
            userDocument.setInstitutionMatchScore(institutionMatch == null ? null : institutionMatch.score());
            userDocument.setCourseApproved(degreeCertificatePolicy.courseApproved(institutionMatch, degreeEvidence));
            userDocument.setApprovalValid(degreeCertificatePolicy.approvalValid(institutionMatch, degreeEvidence));
            userDocument.setBciRegistryVersion(degreeCertificatePolicy.registryVersion());
        } else {
            userDocument.setInstitutionCanonicalName(null);
            userDocument.setInstitutionMatchType(null);
            userDocument.setInstitutionMatchScore(null);
            userDocument.setCourseApproved(null);
            userDocument.setApprovalValid(null);
            userDocument.setBciRegistryVersion(null);
        }
        userDocument.setReviewedBy(null);
        userDocument.setReviewedAt(null);
        userDocument.setReviewDecision(null);
        userDocument.setReviewNotes(null);
        boolean discardPanEvidence = documentType == DocumentType.PAN && discardUploads;
        if (discardPanEvidence) {
            // PAN images are only kept for auto-verified or QR-unreadable manual review.
            userDocument.setStorageReferences(new ArrayList<>());
        } else if (status == VerificationStatus.FAILED && !previousRefs.isEmpty()) {
            // Non-PAN hard failure: keep the last reviewable evidence.
            userDocument.setStorageReferences(previousRefs);
        } else {
            userDocument.setStorageReferences(storageRefs);
        }
        userDocument.setOcrConfidence(extractionResult.confidence());
        userDocument.setQrData(qrData);
        userDocument.setQrRawText(qrData == null ? null : qrData.getRawText());
        userDocument.setQrText(qrText(qrData));
        userDocument.setRequiresManualReview(requiresManualReview);
        userDocument.setPendingReason(pendingReason);
        userDocument.setManualReviewReason(manualReviewReason);
        userDocument.setAutoApproved(status == VerificationStatus.VERIFIED);
        userDocument.setConfidenceScore(extractionResult.confidence());
        userDocument.setUpdatedAt(Instant.now());
        if (documentType == DocumentType.AADHAAR) {
            userDocument.setIsQrVerified(qrVerified);
            userDocument.setQrImageSide(qrScan == null ? null : qrScan.side());
            userDocument.setQrDecodedData(qrDecodedData(qrData));
            // The signed QR remains the authority; keep the verification flag false but
            // retain the field list so the app can explain what differed on the card.
            userDocument.setQrPrintedMismatch(false);
            userDocument.setQrMismatchFields(qrScan != null && !qrScan.mismatchFields().isEmpty()
                    ? qrScan.mismatchFields() : null);
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
        } else if (status == VerificationStatus.REJECTED) {
            userDocument.setFailureReason(degreeDecision != null && degreeDecision.userMessage() != null
                    ? degreeDecision.userMessage() : documentTypeLabel(documentType) + " could not be accepted.");
            userDocument.setFailureFile(failureFile);
        } else {
            userDocument.setFailureReason(null);
            userDocument.setFailureFile(null);
        }

        UserDocument saved = documentRepository.save(userDocument);
        // Only the latest submission is kept. Replace the previously stored assets
        // (including manual-review evidence) instead of orphaning them.
        if (!storageRefs.isEmpty() || discardPanEvidence) {
            Set<String> keptIds = new HashSet<>();
            for (StorageRef ref : storageRefs) {
                if (ref.getPublicId() != null) keptIds.add(ref.getPublicId());
            }
            for (StorageRef old : previousRefs) {
                if (old.getPublicId() != null && !keptIds.contains(old.getPublicId())) {
                    try {
                        storageService.delete(old.getPublicId());
                    } catch (Exception error) {
                        log.warn("Could not delete replaced document image from S3: {}", error.getClass().getSimpleName());
                    }
                }
            }
        }
        if (previousQrPhoto != null && (qrData == null
                || !previousQrPhoto.equals(qrData.getPhotoPublicId()))) {
            try {
                storageService.delete(previousQrPhoto);
            } catch (Exception error) {
                log.warn("Could not delete replaced Aadhaar QR photo: {}", error.getClass().getSimpleName());
            }
        }
        if (previousPanQrPhoto != null && (panQrScan == null
                || !previousPanQrPhoto.equals(panQrScan.photoPublicId()))) {
            try {
                storageService.delete(previousPanQrPhoto);
            } catch (Exception error) {
                log.warn("Could not delete replaced PAN QR photo: {}", error.getClass().getSimpleName());
            }
        }
        if (documentType == DocumentType.AADHAAR && status == VerificationStatus.VERIFIED) {
            reevaluatePendingPan(userId);
        }
        if ((documentType == DocumentType.AADHAAR || documentType == DocumentType.PAN)
                && status == VerificationStatus.VERIFIED) {
            cascadeVerificationAfterIdentityApproved(userId);
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
        fields.put("verificationMethod", "BAR_COUNCIL_NUMBER_FORMAT_CHECK");

        if (request.getAdvocateName() != null && !request.getAdvocateName().isBlank()) {
            fields.put("name", request.getAdvocateName().trim());
        }

        Optional<GoldenIdentity> goldenOpt = findGoldenIdentity(userId);
        boolean identityVerified = goldenOpt.isPresent();
        String candidateName = request.getAdvocateName() != null && !request.getAdvocateName().isBlank()
                ? request.getAdvocateName()
                : identityVerified ? goldenOpt.get().name() : lawyerProfileName(userId);

        AdvocateVerificationApiClient.AdvocateVerificationResult apiResult = advocateVerificationApiClient != null
                ? advocateVerificationApiClient.verifyAdvocate(rawNumber, candidateName)
                : AdvocateVerificationApiClient.AdvocateVerificationResult.active(
                        validationResult.normalizedNumber(), candidateName, validationResult.stateCouncil(), "01/01/" + validationResult.enrollmentYear());

        fields.put("licenseStatus", apiResult.licenseStatus());
        fields.put("apiVerified", String.valueOf(apiResult.verified()));
        if (apiResult.enrollmentDate() != null) {
            fields.put("enrollmentDate", apiResult.enrollmentDate());
        }

        boolean nameMatches = candidateName != null && apiResult.advocateName() != null
                && barCouncilValidationService.isNameMatching(apiResult.advocateName(), candidateName);

        String enrollmentHash = hashEnrollment(validationResult.normalizedNumber());
        Optional<UserDocument> duplicate = enrollmentHash == null ? Optional.empty()
                : documentRepository.findByEnrollmentNumberHash(enrollmentHash);
        boolean duplicateEnrollment = duplicate.isPresent() && !Objects.equals(userId, duplicate.get().getUserId());

        VerificationStatus status;
        String pendingReason = null;
        boolean requiresManualReview = false;
        String manualReviewReason = null;

        if (duplicateEnrollment) {
            status = VerificationStatus.PENDING;
            pendingReason = "BAR_DUPLICATE";
            requiresManualReview = true;
            manualReviewReason = "This Bar Council enrollment number is already registered to another account.";
        } else if (!apiResult.verified()) {
            status = VerificationStatus.PENDING;
            pendingReason = "BAR_INACTIVE";
            requiresManualReview = true;
            manualReviewReason = apiResult.message();
        } else if (identityVerified) {
            if (nameMatches) {
                status = VerificationStatus.VERIFIED;
            } else {
                status = VerificationStatus.PENDING;
                pendingReason = "BAR_MISMATCH";
                requiresManualReview = true;
                manualReviewReason = "Name on Bar Council record (" + apiResult.advocateName()
                        + ") does not match your verified identity (" + goldenOpt.get().name() + ").";
            }
        } else {
            status = VerificationStatus.PENDING;
            pendingReason = "AWAITING_IDENTITY";
            requiresManualReview = false;
            manualReviewReason = "Bar Council enrollment verified with active standing. Verify your Aadhaar or PAN to complete verification automatically.";
        }

        UserDocument userDocument = documentRepository.findByUserIdAndDocumentType(userId, DocumentType.BAR_COUNCIL_ID)
                .orElse(new UserDocument());

        userDocument.setUserId(userId);
        userDocument.setDocumentType(DocumentType.BAR_COUNCIL_ID);
        userDocument.setVerificationStatus(status);
        userDocument.setAutoApproved(status == VerificationStatus.VERIFIED);
        userDocument.setRequiresManualReview(requiresManualReview);
        userDocument.setPendingReason(pendingReason);
        userDocument.setManualReviewReason(manualReviewReason);
        userDocument.setVerifiedAt(status == VerificationStatus.VERIFIED ? Instant.now() : null);
        userDocument.setFailureReason(status == VerificationStatus.VERIFIED ? null : manualReviewReason);
        userDocument.setMaskedDocumentNumber(validationResult.maskedNumber());
        userDocument.setExtractedData(fields);
        userDocument.setEnrollmentNumberHash(enrollmentHash);
        userDocument.setCrossDocumentMismatchFields("BAR_MISMATCH".equals(pendingReason) ? List.of("name") : null);
        userDocument.setOcrConfidence(1.0);
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

        boolean experienced = expOpt.isPresent();
        VerificationChecklist checklist = checklist(userId, documents, experienced);
        boolean identityVerified = checklist.aadhaar() && checklist.pan() && checklist.selfie();
        int completionPercentage = checklist.requiredCount() == 0 ? 0
                : checklist.verifiedCount() * 100 / checklist.requiredCount();
        boolean fullyVerified = checklist.verifiedCount() == checklist.requiredCount();
        overallStatus = fullyVerified ? Verification_Status.VERIFIED : Verification_Status.PENDING;

        List<UserDocument> awaitingIdentityDocs = documents.stream()
                .filter(d -> d.getVerificationStatus() == VerificationStatus.PENDING
                        && "AWAITING_IDENTITY".equals(d.getPendingReason()))
                .toList();

        List<String> missing = missingRequired(checklist, experienced);
        String nextStep;
        if (fullyVerified) {
            nextStep = "All documents required for this role are verified.";
        } else if (!awaitingIdentityDocs.isEmpty() && (!checklist.aadhaar() || !checklist.pan())) {
            List<String> awaitingNames = awaitingIdentityDocs.stream()
                    .map(d -> documentTypeLabel(d.getDocumentType()))
                    .toList();
            nextStep = "Credentials (" + String.join(", ", awaitingNames)
                    + ") verified in active standing. Verify your Aadhaar & PAN to complete verification automatically.";
        } else {
            nextStep = "Missing: " + String.join(", ", missing) + " to complete verification ("
                    + completionPercentage + "% completed).";
        }

        Map<String, Object> summaryDetails = new LinkedHashMap<>();
        summaryDetails.put("totalDocumentsSubmitted", documents.size());
        summaryDetails.put("isFullyVerified", fullyVerified);
        summaryDetails.put("mandatoryDocumentsRequired", checklist.requiredCount());
        summaryDetails.put("mandatoryDocumentsVerified", checklist.verifiedCount());
        summaryDetails.put("selfieVerified", checklist.selfie());
        summaryDetails.put("awaitingIdentityCount", awaitingIdentityDocs.size());
        if (!awaitingIdentityDocs.isEmpty()) {
            summaryDetails.put("documentsAwaitingIdentity", awaitingIdentityDocs.stream()
                    .map(d -> d.getDocumentType().name())
                    .toList());
        }
        summaryDetails.put("roleRequirements", experienced
                ? "Bar enrollment, COP, AIBE, Aadhaar, PAN, live selfie"
                : "Bar enrollment, LL.B. degree, Aadhaar, PAN, live selfie");

        return LawyerVerificationSummaryResponse.builder()
                .userId(userId)
                .lawyerName(name)
                .email(email)
                .role(role)
                .verificationStatus(overallStatus)
                .barCouncilId(barCouncilId)
                .completionPercentage(completionPercentage)
                .barCouncilVerified(checklist.barEnrollment())
                .aadhaarVerified(checklist.aadhaar())
                .panVerified(checklist.pan())
                .identityVerified(identityVerified)
                .barEnrollmentVerified(checklist.barEnrollment())
                .copVerified(checklist.cop())
                .aibeVerified(checklist.aibe())
                .degreeVerified(checklist.degree())
                .requiredDocumentCount(checklist.requiredCount())
                .verifiedDocumentCount(checklist.verifiedCount())
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

        boolean experienced = expOpt.isPresent();
        VerificationChecklist checklist = checklist(userId, allDocs, experienced);
        boolean isFullyVerified = checklist.verifiedCount() == checklist.requiredCount();
        int percentage = checklist.requiredCount() == 0 ? 0
                : checklist.verifiedCount() * 100 / checklist.requiredCount();

        List<String> remainingDocs = missingRequired(checklist, experienced);

        String progressMsg = isFullyVerified
                ? "All required documents are verified."
                : doc.getDocumentType() + " processed (" + percentage + "% complete). Missing: "
                    + String.join(", ", remainingDocs) + ".";

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
                                ? doc.getDocumentType() + " is pending review: " + doc.getManualReviewReason()
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
                                ? doc.getDocumentType() + " is pending review: " + doc.getManualReviewReason()
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

    private String lawyerProfileName(String userId) {
        return findExperiencedLawyer(userId).map(LawyerExperiencedProfile::getName)
                .or(() -> findFresherLawyer(userId).map(LawyerFresherProfile::getName))
                .orElse(null);
    }

    private String documentTypeLabel(DocumentType type) {
        if (type == null) return "Document";
        return switch (type) {
            case AADHAAR -> "Aadhaar Card";
            case PAN -> "PAN Card";
            case BAR_COUNCIL_ID -> "Bar Council ID Card";
            case BAR_CERTIFICATE -> "Bar Enrollment Certificate";
            case CERTIFICATE_OF_PRACTICE -> "Certificate of Practice";
            case DEGREE_CERTIFICATE -> "Law Degree Certificate";
            case AIBE_CERTIFICATE -> "AIBE Certificate";
        };
    }

    private String hashEnrollment(String number) {
        return number == null || number.isBlank() ? null
                : hashIdentity(number.replaceAll("[\\s-]", "").toUpperCase());
    }

    // OCR is required for verification. A failed upload cannot create local identity data.
    private S3StorageService.UploadResult uploadWithOcr(MultipartFile file, String folder, String tag)
            throws Exception {
        return storageService.uploadWithOcr(file, folder, tag);
    }

    private String ocrText(Object rawOcrData) {
        if (rawOcrData == null) return "";
        var candidates = ocrExtractionService.extractTextCandidates(rawOcrData);
        // Use the worker's best-ranked read for policy decisions; field parsing still
        // considers every candidate.
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    private String safeFileName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) return null;
        String name = file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replaceAll("[\\r\\n\\x00-\\x1f]", "");
    }

    private void cleanupStorageAssets(List<StorageRef> refs) {
        for (StorageRef ref : refs) {
            try {
                if (ref.getPublicId() != null && !ref.getPublicId().startsWith("local_")) {
                    storageService.delete(ref.getPublicId());
                }
            } catch (Exception e) {
                log.error("Failed to cleanup S3 asset: {}", ref.getPublicId(), e);
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

    private boolean isVerifiedType(List<UserDocument> documents, DocumentType type) {
        return documents.stream().anyMatch(d -> d.getDocumentType() == type
                && d.getVerificationStatus() == VerificationStatus.VERIFIED);
    }

    private boolean isBarEnrollmentVerified(List<UserDocument> documents) {
        return isVerifiedType(documents, DocumentType.BAR_CERTIFICATE)
                || isVerifiedType(documents, DocumentType.BAR_COUNCIL_ID);
    }

    private record VerificationChecklist(boolean barEnrollment, boolean cop, boolean aibe, boolean degree,
            boolean aadhaar, boolean pan, boolean selfie, int verifiedCount, int requiredCount) {}

    /** Role-based mandatory set: experienced needs COP+AIBE, freshers need an LL.B. degree. */
    private VerificationChecklist checklist(String userId, List<UserDocument> documents, boolean experienced) {
        boolean bar = isBarEnrollmentVerified(documents);
        boolean cop = isVerifiedType(documents, DocumentType.CERTIFICATE_OF_PRACTICE);
        boolean aibe = isVerifiedType(documents, DocumentType.AIBE_CERTIFICATE);
        boolean degree = isVerifiedType(documents, DocumentType.DEGREE_CERTIFICATE);
        boolean aadhaar = documents.stream().anyMatch(this::isVerifiedAadhaar);
        boolean pan = isVerifiedType(documents, DocumentType.PAN);
        boolean selfie = documents.stream().anyMatch(d -> hasVerifiedSelfie(userId, d));
        int required = experienced ? 6 : 5;
        int verified = (bar ? 1 : 0) + (aadhaar ? 1 : 0) + (pan ? 1 : 0) + (selfie ? 1 : 0)
                + (experienced ? (cop ? 1 : 0) + (aibe ? 1 : 0) : (degree ? 1 : 0));
        return new VerificationChecklist(bar, cop, aibe, degree, aadhaar, pan, selfie, verified, required);
    }

    private List<String> missingRequired(VerificationChecklist c, boolean experienced) {
        List<String> missing = new ArrayList<>();
        if (!c.barEnrollment()) missing.add("Bar Council enrollment certificate");
        if (experienced && !c.cop()) missing.add("Certificate of Practice");
        if (experienced && !c.aibe()) missing.add("AIBE certificate");
        if (!experienced && !c.degree()) missing.add("LL.B. degree certificate");
        if (!c.aadhaar()) missing.add("Aadhaar Card");
        if (!c.pan()) missing.add("PAN Card");
        if (!c.selfie()) missing.add("Live Selfie");
        return missing;
    }

    private boolean hasAllRequiredDocuments(String userId, List<UserDocument> documents) {
        boolean experienced = findExperiencedLawyer(userId).isPresent();
        VerificationChecklist c = checklist(userId, documents, experienced);
        return c.verifiedCount() == c.requiredCount();
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
                StorageRef ref = storageService.uploadBytes(bytes,
                        "documents/PAN", "qr");
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

    private String fullGender(String value) {
        if (value == null) return null;
        return switch (value.trim().toUpperCase()) {
            case "M", "MALE" -> "MALE";
            case "F", "FEMALE" -> "FEMALE";
            case "T", "TRANSGENDER" -> "TRANSGENDER";
            default -> value;
        };
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
        if (savedPan.getVerificationStatus() == VerificationStatus.VERIFIED) {
            cascadeVerificationAfterIdentityApproved(userId);
        }
        log.info("PAN re-evaluated after Aadhaar verification: status={}", evaluation.status());
    }

    /**
     * When Aadhaar or PAN is verified, re-evaluates all pending professional documents
     * (Bar Council Certificate/ID, Degree, AIBE) against the newly established Golden Identity.
     * Documents matching the Golden Record are automatically approved to VERIFIED.
     */
    public void cascadeVerificationAfterIdentityApproved(String userId) {
        Optional<GoldenIdentity> goldenOpt = findGoldenIdentity(userId);
        if (goldenOpt.isEmpty()) return;
        GoldenIdentity golden = goldenOpt.get();

        List<UserDocument> pendingDocs = documentRepository.findByUserId(userId).stream()
                .filter(d -> d.getVerificationStatus() == VerificationStatus.PENDING)
                .toList();

        for (UserDocument doc : pendingDocs) {
            DocumentType type = doc.getDocumentType();
            if (type == DocumentType.AADHAAR || type == DocumentType.PAN) continue;

            String reason = doc.getPendingReason();
            if ("AWAITING_IDENTITY".equals(reason)
                    || "BAR_REVIEW".equals(reason)
                    || "DEGREE_REVIEW".equals(reason)
                    || "AIBE_REVIEW".equals(reason)
                    || "BAR_MISMATCH".equals(reason)
                    || "DEGREE_MISMATCH".equals(reason)
                    || "AIBE_MISMATCH".equals(reason)) {

                String name = doc.getExtractedData() != null ? doc.getExtractedData().get("name") : null;
                boolean isBar = type == DocumentType.BAR_COUNCIL_ID
                        || type == DocumentType.BAR_CERTIFICATE
                        || type == DocumentType.CERTIFICATE_OF_PRACTICE;

                if (name != null && barCouncilValidationService.isNameMatching(name, golden.name())) {
                    boolean duplicate = false;
                    if (isBar) {
                        String enrollmentHash = doc.getEnrollmentNumberHash();
                        if (enrollmentHash != null) {
                            Optional<UserDocument> dup = documentRepository.findByEnrollmentNumberHash(enrollmentHash);
                            duplicate = dup.isPresent() && !Objects.equals(userId, dup.get().getUserId());
                        }
                    }
                    if (!duplicate) {
                        doc.setVerificationStatus(VerificationStatus.VERIFIED);
                        doc.setAutoApproved(true);
                        doc.setRequiresManualReview(false);
                        doc.setPendingReason(null);
                        doc.setManualReviewReason(null);
                        doc.setFailureReason(null);
                        doc.setVerifiedAt(Instant.now());
                        doc.setUpdatedAt(Instant.now());
                        UserDocument saved = documentRepository.save(doc);
                        applyDocumentVerificationToLawyer(userId, saved);
                        log.info("Document {} auto-verified for user {} following identity verification cascade", type, userId);
                    }
                }
            }
        }
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

    /**
     * Applies a scanned barcode/QR payload from a Bar Council ID card. The code is a
     * consistency check against the printed enrollment number; it never proves
     * authenticity on its own.
     */
    public UserDocument applyBarIdCode(String userId, String codeText, String format) {
        validateExistingLawyer(userId);
        if (codeText == null || codeText.isBlank()) {
            throw new DocumentVerificationException("The scanned card code is empty.");
        }
        UserDocument doc = documentRepository.findByUserIdAndDocumentType(userId, DocumentType.BAR_COUNCIL_ID)
                .orElseGet(UserDocument::new);
        doc.setUserId(userId);
        doc.setDocumentType(DocumentType.BAR_COUNCIL_ID);

        Map<String, String> fields = new LinkedHashMap<>(doc.getExtractedData() == null
                ? Map.of() : doc.getExtractedData());
        fields.put("barcodeFormat", format == null ? "SCANNED" : format);
        fields.put("barcodePayload", codeText.length() > 240 ? codeText.substring(0, 240) : codeText);

        String codeEnrollment = enrollmentFromCode(codeText);
        String ocrEnrollment = fields.get("enrollmentNumber");
        if (codeEnrollment != null) {
            fields.put("enrollmentNumber", codeEnrollment);
            var validation = barCouncilValidationService.validate(codeEnrollment);
            if (validation.valid()) {
                fields.put("stateCode", validation.stateCode());
                fields.put("stateCouncil", validation.stateCouncil());
                fields.put("enrollmentYear", String.valueOf(validation.enrollmentYear()));
                fields.put("sequenceNumber", validation.sequenceNumber());
                doc.setMaskedDocumentNumber(validation.maskedNumber());
            }
        }
        String codeName = nameFromCode(codeText);
        if (codeName != null && !codeName.isBlank()) {
            fields.put("name", codeName);
        }
        if (codeEnrollment == null) {
            doc.setVerificationStatus(VerificationStatus.PENDING);
            doc.setRequiresManualReview(true);
            doc.setPendingReason("BAR_ID_CODE_UNPARSED");
            doc.setManualReviewReason("The scanned card code could not be matched to an enrollment number. Sent for manual review.");
            doc.setFailureReason(doc.getManualReviewReason());
        } else if (ocrEnrollment != null && !sameEnrollment(codeEnrollment, ocrEnrollment)) {
            doc.setVerificationStatus(VerificationStatus.PENDING);
            doc.setRequiresManualReview(true);
            doc.setPendingReason("BAR_ID_BARCODE_MISMATCH");
            doc.setManualReviewReason("The scanned card code does not match the enrollment number printed on the card. Sent for manual review.");
            doc.setFailureReason(doc.getManualReviewReason());
        } else {
            fields.put("barcodeEnrollmentNumber", codeEnrollment);
            fields.put("barcodeMatchesOcr", ocrEnrollment != null ? "true" : "not_applicable");
            String codeHash = hashEnrollment(codeEnrollment);
            doc.setEnrollmentNumberHash(codeHash);
            boolean duplicate = false;
            if (codeHash != null) {
                Optional<UserDocument> dup = documentRepository.findByEnrollmentNumberHash(codeHash);
                duplicate = dup.isPresent() && !Objects.equals(userId, dup.get().getUserId());
            }
            Optional<GoldenIdentity> golden = findGoldenIdentity(userId);
            String cardName = fields.get("name");
            if (duplicate) {
                doc.setVerificationStatus(VerificationStatus.PENDING);
                doc.setRequiresManualReview(true);
                doc.setPendingReason("BAR_DUPLICATE");
                doc.setManualReviewReason("This Bar Council enrollment number is already registered to another account. Manual review is in progress.");
                doc.setFailureReason(doc.getManualReviewReason());
            } else if (golden.isEmpty()) {
                doc.setVerificationStatus(VerificationStatus.PENDING);
                doc.setRequiresManualReview(false);
                doc.setPendingReason("AWAITING_IDENTITY");
                doc.setManualReviewReason("Card code matched the card. Verify your Aadhaar or PAN to complete verification automatically.");
                doc.setFailureReason(doc.getManualReviewReason());
            } else if (cardName == null || !barCouncilValidationService.isNameMatching(cardName, golden.get().name())) {
                doc.setVerificationStatus(VerificationStatus.PENDING);
                doc.setRequiresManualReview(true);
                doc.setPendingReason("BAR_ID_NAME_MISMATCH");
                doc.setManualReviewReason("The name on the card does not match your verified Aadhaar/PAN name. Sent for manual review.");
                doc.setFailureReason(doc.getManualReviewReason());
            } else {
                doc.setVerificationStatus(VerificationStatus.VERIFIED);
                doc.setAutoApproved(true);
                doc.setRequiresManualReview(false);
                doc.setPendingReason(null);
                doc.setManualReviewReason(null);
                doc.setFailureReason(null);
                doc.setFailureFile(null);
                doc.setVerifiedAt(Instant.now());
            }
        }
        doc.setExtractedData(fields);
        doc.setOcrConfidence(doc.getOcrConfidence() == null ? 1.0 : doc.getOcrConfidence());
        doc.setUpdatedAt(Instant.now());
        UserDocument saved = documentRepository.save(doc);
        applyDocumentVerificationToLawyer(userId, saved);
        return saved;
    }

    private String nameFromCode(String code) {
        if (code == null) return null;
        String decoded = code;
        try {
            decoded = java.net.URLDecoder.decode(code, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        Matcher labeled = Pattern.compile(
                "(?i)[\"']?(?:name|advocate_name|candidate_name)[\"']?\\s*[:=]\\s*[\"']?"
                        + "([A-Za-z][A-Za-z .'-]{2,60}?)(?=[&\"'}`]|$)").matcher(decoded);
        if (labeled.find()) return normalizeText(labeled.group(1));
        return null;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }

    private String enrollmentFromCode(String code) {        if (code == null) return null;
        Matcher general = Pattern.compile(
                "(?i)\\b([A-Z]{1,4}[/-]\\s*\\d{1,6}[/-]\\s*\\d{4})\\b").matcher(code);
        if (general.find()) {
            var validation = barCouncilValidationService.validate(general.group(1));
            if (validation.valid()) return validation.normalizedNumber();
        }
        Matcher labeled = Pattern.compile(
                "(?i)(?:enrollment|enrolment|enrollment_no|enrolment_no|reg(?:istration)?|roll)"
                        + "[^A-Za-z0-9]{0,4}([A-Z0-9]{0,4}[/-]?\\d{1,6}[/-]\\d{4})").matcher(code);
        if (labeled.find()) {
            var validation = barCouncilValidationService.validate(labeled.group(1));
            if (validation.valid()) return validation.normalizedNumber();
        }
        var direct = barCouncilValidationService.validate(code.trim());
        return direct.valid() ? direct.normalizedNumber() : null;
    }

    private boolean sameEnrollment(String first, String second) {
        String a = first == null ? "" : first.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String b = second == null ? "" : second.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return !a.isBlank() && a.equals(b);
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
                storageService.delete(scan.photoPublicId());
            } catch (Exception error) {
                log.warn("Could not delete unused PAN QR photo: {}", error.getClass().getSimpleName());
            }
        }
        if (previousPhoto != null && (scan.photoPublicId() == null
                || !previousPhoto.equals(scan.photoPublicId()))) {
            try {
                storageService.delete(previousPhoto);
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
        return approveDocument(documentId, adminId, notes, Map.of());
    }

    public UserDocument approveDocument(String documentId, String adminId, String notes,
                                        Map<String, Boolean> checklist) {
        UserDocument doc = requireReviewableDocument(documentId);
        validateDegreeReviewChecklist(doc, checklist);
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
        doc.setCrossDocumentMismatchFields(null);
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
        doc.setReviewChecklist(checklist == null || checklist.isEmpty() ? null : new LinkedHashMap<>(checklist));
        doc.setUpdatedAt(Instant.now());
        UserDocument saved = documentRepository.save(doc);
        applyDocumentVerificationToLawyer(doc.getUserId(), saved);
        if (doc.getDocumentType() == DocumentType.AADHAAR || doc.getDocumentType() == DocumentType.PAN) {
            cascadeVerificationAfterIdentityApproved(doc.getUserId());
        }
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

    private static final List<String> DEGREE_REQUIRED_CHECKLIST = List.of(
            "degree_is_llb", "final_or_provisional", "name_matches_kyc", "university_bci_recognized");

    private void validateDegreeReviewChecklist(UserDocument doc, Map<String, Boolean> checklist) {
        if (doc.getDocumentType() != DocumentType.DEGREE_CERTIFICATE) return;
        List<String> required = new ArrayList<>(DEGREE_REQUIRED_CHECKLIST);
        if (doc.getVerificationFlags() != null
                && doc.getVerificationFlags().contains("REQUIRES_BCI_EQUIVALENCE")) {
            required.add("foreign_equivalence");
        }
        List<String> missing = required.stream()
                .filter(key -> checklist == null || !Boolean.TRUE.equals(checklist.get(key)))
                .toList();
        if (!missing.isEmpty()) {
            throw new DocumentVerificationException(
                    "Complete the degree review checklist before approving: " + String.join(", ", missing));
        }
    }

    private UserDocument requireReviewableDocument(String documentId) {        UserDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentVerificationException("Document not found."));
        if (doc.getDocumentType() == null) {
            throw new DocumentVerificationException("Document type is missing.");
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
        response.setPendingReason(doc.getPendingReason());
        response.setVerifiedAt(doc.getVerifiedAt());
        return response;
    }
}
