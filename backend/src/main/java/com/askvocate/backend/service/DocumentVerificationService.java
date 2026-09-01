package com.askvocate.backend.service;

import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.CloudinaryRef;
import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.model.UserDocument;
import com.askvocate.backend.model.VerificationStatus;
import com.askvocate.backend.repository.UserDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full document verification flow:
 * validate → upload to Cloudinary → OCR extract → persist to MongoDB.
 * 
 * <p><b>Security invariants:</b>
 * <ul>
 *   <li>{@code userId} is always sourced from the JWT principal, never from request input.</li>
 *   <li>All database queries are scoped by {@code userId} to prevent cross-user access.</li>
 *   <li>Raw OCR text is never stored — only parsed fields and masked document numbers.</li>
 * </ul>
 */
@Service
public class DocumentVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentVerificationService.class);

    private final CloudinaryService cloudinaryService;
    private final OcrExtractionService ocrExtractionService;
    private final UserDocumentRepository documentRepository;

    public DocumentVerificationService(CloudinaryService cloudinaryService,
                                       OcrExtractionService ocrExtractionService,
                                       UserDocumentRepository documentRepository) {
        this.cloudinaryService = cloudinaryService;
        this.ocrExtractionService = ocrExtractionService;
        this.documentRepository = documentRepository;
    }

    /**
     * Verifies a user's identity document.
     *
     * @param userId       authenticated user ID from JWT
     * @param documentType the document type being submitted
     * @param frontImage   front side image of the document
     * @param backImage    back side image (required for Aadhaar, optional for others)
     * @return sanitized verification response
     */
    public DocumentVerificationResponse verifyDocument(String userId,
                                                        DocumentType documentType,
                                                        MultipartFile frontImage,
                                                        MultipartFile backImage) {
        // 1. Validate inputs
        validateRequest(userId, documentType, frontImage, backImage);

        // 2. Check for existing verification of this document type
        if (documentRepository.existsByUserIdAndDocumentType(userId, documentType)) {
            throw new DocumentVerificationException(
                    "You already have a " + documentType + " verification on file. "
                    + "Please contact support to re-verify.");
        }

        String folder = "askvocate/documents/" + userId + "/" + documentType.name();
        List<CloudinaryRef> cloudinaryRefs = new ArrayList<>();
        OcrExtractionService.ExtractionResult extractionResult;

        try {
            // 3. Upload front image with OCR
            log.info("Uploading front image for userId={}, documentType={}", userId, documentType);
            CloudinaryService.UploadResult frontResult =
                    cloudinaryService.uploadWithOcr(frontImage, folder, "front");
            cloudinaryRefs.add(frontResult.cloudinaryRef());

            // 4. Extract OCR from front
            OcrExtractionService.ExtractionResult frontExtraction =
                    ocrExtractionService.extract(frontResult.rawOcrData(), documentType);

            // 5. Upload and extract back image if provided
            if (backImage != null && !backImage.isEmpty()) {
                log.info("Uploading back image for userId={}, documentType={}", userId, documentType);
                CloudinaryService.UploadResult backResult =
                        cloudinaryService.uploadWithOcr(backImage, folder, "back");
                cloudinaryRefs.add(backResult.cloudinaryRef());

                OcrExtractionService.ExtractionResult backExtraction =
                        ocrExtractionService.extract(backResult.rawOcrData(), documentType);

                // Merge front + back results
                extractionResult = ocrExtractionService.mergeResults(frontExtraction, backExtraction);
            } else {
                extractionResult = frontExtraction;
            }

        } catch (Exception e) {
            // If upload/OCR fails, clean up any uploaded images
            try {
                cleanupCloudinaryAssets(cloudinaryRefs);
            } catch (Exception cleanupException) {
                log.warn("Failed to clean up Cloudinary assets after verification failure", cleanupException);
            }
            throw new DocumentVerificationException("Document verification failed: " + e.getMessage(), e);
        }

        // 6. Determine verification status
        VerificationStatus status = extractionResult.success()
                ? VerificationStatus.VERIFIED
                : VerificationStatus.FAILED;

        // 7. Build and persist UserDocument
        UserDocument userDocument = new UserDocument();
        userDocument.setUserId(userId);
        userDocument.setDocumentType(documentType);
        userDocument.setVerificationStatus(status);
        userDocument.setExtractedData(extractionResult.extractedFields());
        userDocument.setMaskedDocumentNumber(extractionResult.maskedDocumentNumber());
        userDocument.setCloudinaryReferences(cloudinaryRefs);
        userDocument.setOcrConfidence(extractionResult.confidence());

        if (!extractionResult.success()) {
            userDocument.setFailureReason(extractionResult.error());
        }

        UserDocument saved = documentRepository.save(userDocument);
        log.info("Document verification saved: id={}, userId={}, type={}, status={}",
                saved.getId(), userId, documentType, status);

        return toResponse(saved);
    }

    /**
     * Returns all verification documents for the authenticated user.
     */
    public List<DocumentVerificationResponse> getUserDocuments(String userId) {
        return documentRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns a specific verification document, enforcing user ownership.
     *
     * @throws DocumentVerificationException if document not found or belongs to another user
     */
    public DocumentVerificationResponse getDocumentById(String userId, String documentId) {
        UserDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentVerificationException("Document not found."));

        // Enforce user ownership — never allow cross-user access
        if (!doc.getUserId().equals(userId)) {
            // Return generic "not found" to avoid leaking existence of other users' docs
            throw new DocumentVerificationException("Document not found.");
        }

        return toResponse(doc);
    }

    // ── Validation ──────────────────────────────────────────────────────

    private void validateRequest(String userId, DocumentType documentType,
                                  MultipartFile frontImage, MultipartFile backImage) {
        if (userId == null || userId.isBlank()) {
            throw new DocumentVerificationException("User authentication is required.");
        }

        if (documentType == null) {
            throw new DocumentVerificationException(
                    "Document type is required. Supported types: AADHAAR, PAN, DRIVING_LICENSE.");
        }

        if (frontImage == null || frontImage.isEmpty()) {
            throw new DocumentVerificationException("Front image of the document is required.");
        }

        // Aadhaar requires both front and back
        if (documentType == DocumentType.AADHAAR && (backImage == null || backImage.isEmpty())) {
            throw new DocumentVerificationException(
                    "Both front and back images are required for Aadhaar card verification.");
        }
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

    private void cleanupCloudinaryAssets(List<CloudinaryRef> refs) {
        for (CloudinaryRef ref : refs) {
            try {
                cloudinaryService.delete(ref.getPublicId());
            } catch (Exception e) {
                log.error("Failed to cleanup Cloudinary asset: {}", ref.getPublicId(), e);
            }
        }
    }

    // ── Mapping ─────────────────────────────────────────────────────────

    /**
     * Maps a {@link UserDocument} entity to a safe, client-facing response DTO.
     * Internal Cloudinary public IDs and raw data are excluded.
     */
    private DocumentVerificationResponse toResponse(UserDocument doc) {
        return DocumentVerificationResponse.from(
                doc.getId(),
                doc.getDocumentType(),
                doc.getVerificationStatus(),
                doc.getMaskedDocumentNumber(),
                doc.getExtractedData(),
                doc.getOcrConfidence(),
                doc.getFailureReason(),
                doc.getCreatedAt()
        );
    }
}
