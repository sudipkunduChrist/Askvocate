package com.askvocate.backend.service;

import com.askvocate.backend.dto.BarCouncilVerificationRequest;
import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.dto.LawyerVerificationSummaryResponse;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.ClientProfileRepository;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import com.askvocate.backend.repository.UserDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

/**
 * Orchestrates document verification for lawyers and clients.
 * Supports Aadhaar, PAN card, and Bar Council credentials (ID card, Enrollment Certificate, COP).
 * Automatically updates lawyer profile verification status and barCouncilId upon successful verification.
 */
@Service
public class DocumentVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentVerificationService.class);

    private final CloudinaryService cloudinaryService;
    private final OcrExtractionService ocrExtractionService;
    private final BarCouncilValidationService barCouncilValidationService;
    private final UserDocumentRepository documentRepository;
    private final LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository;
    private final LawyerFresherProfileRepository lawyerFresherProfileRepository;
    private final ClientProfileRepository clientProfileRepository;

    public DocumentVerificationService(CloudinaryService cloudinaryService,
                                       OcrExtractionService ocrExtractionService,
                                       BarCouncilValidationService barCouncilValidationService,
                                       UserDocumentRepository documentRepository,
                                       LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository,
                                       LawyerFresherProfileRepository lawyerFresherProfileRepository,
                                       ClientProfileRepository clientProfileRepository) {
        this.cloudinaryService = cloudinaryService;
        this.ocrExtractionService = ocrExtractionService;
        this.barCouncilValidationService = barCouncilValidationService;
        this.documentRepository = documentRepository;
        this.lawyerExperiencedProfileRepository = lawyerExperiencedProfileRepository;
        this.lawyerFresherProfileRepository = lawyerFresherProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
    }

    /**
     * Verifies a user's identity or professional document.
     *
     * @param userId       user identifier
     * @param documentType document type (AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, etc.)
     * @param frontImage   front side image
     * @param backImage    back side image (required for Aadhaar, optional for others)
     * @return verification response DTO with masked number and extracted fields
     */
    public DocumentVerificationResponse verifyDocument(String userId,
                                                        DocumentType documentType,
                                                        MultipartFile frontImage,
                                                        MultipartFile backImage) {
        // 1. Validate inputs
        validateRequest(userId, documentType, frontImage, backImage);

        String folder = "askvocate/documents/" + userId + "/" + documentType.name();
        List<CloudinaryRef> cloudinaryRefs = new ArrayList<>();
        OcrExtractionService.ExtractionResult extractionResult;

        try {
            // 2. Upload front image with OCR
            log.info("Uploading front image for userId={}, documentType={}", userId, documentType);
            CloudinaryService.UploadResult frontResult = uploadWithFallback(frontImage, folder, "front");
            cloudinaryRefs.add(frontResult.cloudinaryRef());

            // 3. Extract OCR from front
            OcrExtractionService.ExtractionResult frontExtraction =
                    ocrExtractionService.extract(frontResult.rawOcrData(), documentType, "front");

            // 4. Upload and extract back image if provided
            if (backImage != null && !backImage.isEmpty()) {
                log.info("Uploading back image for userId={}, documentType={}", userId, documentType);
                CloudinaryService.UploadResult backResult = uploadWithFallback(backImage, folder, "back");
                cloudinaryRefs.add(backResult.cloudinaryRef());

                OcrExtractionService.ExtractionResult backExtraction =
                        ocrExtractionService.extract(backResult.rawOcrData(), documentType, "back");

                // Merge front + back results
                extractionResult = ocrExtractionService.mergeResults(frontExtraction, backExtraction);
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
            throw new DocumentVerificationException("Document verification failed: " + e.getMessage(), e);
        }

        // 5. Determine verification status
        VerificationStatus status = extractionResult.success()
                ? VerificationStatus.VERIFIED
                : VerificationStatus.FAILED;

        // If verification failed, delete the uploaded image from Cloudinary immediately to avoid storing junk/invalid files
        if (!extractionResult.success()) {
            log.info("Document verification failed: cleaning up uploaded assets from Cloudinary for userId={}, documentType={}",
                    userId, documentType);
            cleanupCloudinaryAssets(cloudinaryRefs);
            cloudinaryRefs = new ArrayList<>();
        }

        // 6. Look up existing document record or create a new one (supports re-verification)
        UserDocument userDocument = documentRepository.findByUserIdAndDocumentType(userId, documentType)
                .orElse(new UserDocument());

        userDocument.setUserId(userId);
        userDocument.setDocumentType(documentType);
        userDocument.setVerificationStatus(status);
        userDocument.setExtractedData(extractionResult.extractedFields());
        userDocument.setMaskedDocumentNumber(extractionResult.maskedDocumentNumber());
        userDocument.setCloudinaryReferences(cloudinaryRefs);
        userDocument.setOcrConfidence(extractionResult.confidence());
        userDocument.setUpdatedAt(Instant.now());

        if (!extractionResult.success()) {
            userDocument.setFailureReason(extractionResult.error());
        } else {
            userDocument.setFailureReason(null);
        }

        UserDocument saved = documentRepository.save(userDocument);
        log.info("Document verification saved: id={}, userId={}, type={}, status={}",
                saved.getId(), userId, documentType, status);

        // 7. Update Lawyer Profile credentials and verification status if applicable
        LawyerUpdateResult lawyerUpdate = applyDocumentVerificationToLawyer(userId, saved);

        return toResponse(saved, lawyerUpdate);
    }

    /**
     * Direct Bar Council Number verification API for lawyers.
     * Allows validating State Bar Council enrollment number format, state code, and year,
     * instantly updating lawyer profile and recording verification status.
     */
    public DocumentVerificationResponse verifyBarCouncilDirect(BarCouncilVerificationRequest request) {
        String userId = request.getUserId();
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
        ) || (barCouncilId != null && !barCouncilId.isBlank());

        boolean aadhaarVerified = documents.stream().anyMatch(d ->
                d.getDocumentType() == DocumentType.AADHAAR
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        boolean panVerified = documents.stream().anyMatch(d ->
                d.getDocumentType() == DocumentType.PAN
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        boolean identityVerified = aadhaarVerified && panVerified;

        int verifiedCount = (barCouncilVerified ? 1 : 0) + (aadhaarVerified ? 1 : 0) + (panVerified ? 1 : 0);
        int completionPercentage = switch (verifiedCount) {
            case 3 -> 100;
            case 2 -> 67;
            case 1 -> 33;
            default -> 0;
        };

        List<String> missing = new ArrayList<>();
        if (!barCouncilVerified) missing.add("Bar Council ID / Certificate");
        if (!aadhaarVerified) missing.add("Aadhaar Card (front + back)");
        if (!panVerified) missing.add("PAN Card");

        String nextStep = missing.isEmpty()
                ? "All 3 mandatory documents (Bar Council, Aadhaar, PAN) are fully verified. 100% document verification process complete!"
                : "Missing: " + String.join(" and ", missing) + " to achieve 100% document verification process complete (" + completionPercentage + "% completed).";

        Map<String, Object> summaryDetails = new LinkedHashMap<>();
        summaryDetails.put("totalDocumentsSubmitted", documents.size());
        summaryDetails.put("isFullyVerified", completionPercentage == 100);
        summaryDetails.put("mandatoryDocumentsRequired", 3);
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
        return documentRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns a specific verification document by ID, checking user ownership if userId is non-empty.
     */
    public DocumentVerificationResponse getDocumentById(String userId, String documentId) {
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

        boolean hasAadhaarVerified = allDocs.stream().anyMatch(d ->
                d.getDocumentType() == DocumentType.AADHAAR
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        boolean hasPanVerified = allDocs.stream().anyMatch(d ->
                d.getDocumentType() == DocumentType.PAN
                        && d.getVerificationStatus() == VerificationStatus.VERIFIED
        );

        int verifiedCount = (hasBarCouncilVerified ? 1 : 0) + (hasAadhaarVerified ? 1 : 0) + (hasPanVerified ? 1 : 0);
        int percentage = switch (verifiedCount) {
            case 3 -> 100;
            case 2 -> 67;
            case 1 -> 33;
            default -> 0;
        };
        boolean isFullyVerified = verifiedCount == 3;

        List<String> remainingDocs = new ArrayList<>();
        if (!hasBarCouncilVerified) remainingDocs.add("Bar Council ID / Certificate");
        if (!hasAadhaarVerified) remainingDocs.add("Aadhaar Card");
        if (!hasPanVerified) remainingDocs.add("PAN Card");

        String progressMsg = isFullyVerified
                ? "All 3 mandatory documents (Bar Council, Aadhaar, PAN) verified! 100% document verification process complete."
                : doc.getDocumentType() + " verified successfully (" + percentage + "% complete). Missing: " + String.join(", ", remainingDocs) + " to achieve 100% document verification process complete.";

        if (expOpt.isEmpty() && fresherOpt.isEmpty()) {
            // Auto-create & store new lawyer profile from verified document
            String lawyerName = (extractedName != null && !extractedName.isBlank()) ? extractedName : "Adv. Promila";
            String email = (userId != null && userId.contains("@"))
                    ? userId
                    : (lawyerName.toLowerCase().replaceAll("[^a-z0-9]", "") + "@askvocate.com");
            String address = doc.getExtractedData() != null ? doc.getExtractedData().getOrDefault("address", "") : "";

            LawyerExperiencedProfile newLawyer = new LawyerExperiencedProfile();
            newLawyer.setName(lawyerName);
            newLawyer.setEmail(email);
            newLawyer.setAddress(address);
            if (isBarCouncilDoc && enrollmentNum != null && !enrollmentNum.isBlank()) {
                newLawyer.setBarCouncilId(enrollmentNum);
            }
            newLawyer.setVerificationStatus(isFullyVerified ? Verification_Status.VERIFIED : Verification_Status.PENDING);
            newLawyer.setVerifiedAt(isFullyVerified ? System.currentTimeMillis() : null);
            LawyerExperiencedProfile savedLawyer = lawyerExperiencedProfileRepository.save(newLawyer);

            log.info("Auto-registered new lawyer in MongoDB: id={}, name={}, email={}, status={}",
                    savedLawyer.getId(), savedLawyer.getName(), savedLawyer.getEmail(), newLawyer.getVerificationStatus());

            return new LawyerUpdateResult(true, newLawyer.getVerificationStatus(), true, progressMsg);
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
                return new LawyerUpdateResult(true, exp.getVerificationStatus(), nameMatch,
                        "Document verification failed: " + doc.getFailureReason());
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
                return new LawyerUpdateResult(true, fresher.getVerificationStatus(), nameMatch,
                        "Document verification failed: " + doc.getFailureReason());
            }
        }
    }

    private Optional<LawyerExperiencedProfile> findExperiencedLawyer(String userId) {
        Optional<LawyerExperiencedProfile> opt = lawyerExperiencedProfileRepository.findById(userId);
        if (opt.isPresent()) return opt;
        return lawyerExperiencedProfileRepository.findByEmail(userId);
    }

    private Optional<LawyerFresherProfile> findFresherLawyer(String userId) {
        Optional<LawyerFresherProfile> opt = lawyerFresherProfileRepository.findById(userId);
        if (opt.isPresent()) return opt;
        return lawyerFresherProfileRepository.findByEmail(userId);
    }

    // ── Cloudinary Upload with Fallback ──────────────────────────────────

    private CloudinaryService.UploadResult uploadWithFallback(MultipartFile file, String folder, String tag)
            throws Exception {
        try {
            return cloudinaryService.uploadWithOcr(file, folder, tag);
        } catch (Exception e) {
            log.warn("Cloudinary upload with OCR failed ({}), attempting standard upload or fallback metadata", e.getMessage());
            try {
                String secureUrl = cloudinaryService.uploadFile(file, folder);
                CloudinaryRef ref = new CloudinaryRef();
                ref.setSecureUrl(secureUrl);
                ref.setPublicId(cloudinaryService.extractPublicId(secureUrl));
                return new CloudinaryService.UploadResult(ref, Map.of());
            } catch (Exception uploadEx) {
                log.warn("Cloudinary upload failed entirely ({}), creating local verification reference", uploadEx.getMessage());
                CloudinaryRef mockRef = new CloudinaryRef();
                mockRef.setPublicId("local_" + UUID.randomUUID());
                mockRef.setSecureUrl("local://askvocate/documents/" + file.getOriginalFilename());
                return new CloudinaryService.UploadResult(mockRef, Map.of());
            }
        }
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

    // ── Validation ──────────────────────────────────────────────────────

    private void validateRequest(String userId, DocumentType documentType,
                                  MultipartFile frontImage, MultipartFile backImage) {
        if (userId == null || userId.isBlank()) {
            throw new DocumentVerificationException("User identifier is required.");
        }

        if (documentType == null) {
            throw new DocumentVerificationException(
                    "Document type is required. Supported types: AADHAAR, PAN, BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE, DRIVING_LICENSE.");
        }

        if (frontImage == null || frontImage.isEmpty()) {
            throw new DocumentVerificationException("Front image of the document is required.");
        }

        if (documentType == DocumentType.AADHAAR && (backImage == null || backImage.isEmpty())) {
            throw new DocumentVerificationException(
                    "Both front and back images are required for Aadhaar card verification.");
        }
    }

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

        return DocumentVerificationResponse.from(
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
    }
}
