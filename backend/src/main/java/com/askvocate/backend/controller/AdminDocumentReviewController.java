package com.askvocate.backend.controller;

import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.UserDocumentRepository;
import com.askvocate.backend.service.AppSessionService;
import com.askvocate.backend.service.CloudinaryService;
import com.askvocate.backend.service.DocumentVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/** Admin-only document review queue, currently focused on PAN manual review. */
@RestController
@RequestMapping("/api/admin/verification/documents")
public class AdminDocumentReviewController {

    private final DocumentVerificationService verification;
    private final UserDocumentRepository documents;
    private final AppSessionService sessions;
    private final CloudinaryService cloudinaryService;

    public AdminDocumentReviewController(DocumentVerificationService verification,
            UserDocumentRepository documents, AppSessionService sessions,
            CloudinaryService cloudinaryService) {
        this.verification = verification;
        this.documents = documents;
        this.sessions = sessions;
        this.cloudinaryService = cloudinaryService;
    }

    public record AdminDocumentResponse(
            String id, String userId, DocumentType documentType,
            VerificationStatus verificationStatus, String maskedDocumentNumber, String panNumber,
            Map<String, String> extractedData,
            List<String> crossDocumentMismatchFields, Boolean requiresManualReview,
            String manualReviewReason, String failureReason, String reviewedBy,
            Instant reviewedAt, String reviewDecision, String reviewNotes,
            List<CloudinaryRef> images, Map<String, String> aadhaarQrData,
            Map<String, String> panQrData, Boolean isPanQrVerified, String panQrPhotoUrl,
            Instant submittedAt, Instant verifiedAt) {}

    @GetMapping("/pending")
    public List<AdminDocumentResponse> pending(
            @RequestParam(value = "type", required = false) String type) {
        List<UserDocument> pending = type == null || type.isBlank()
                ? documents.findByRequiresManualReviewTrueAndDocumentType(DocumentType.PAN)
                : documents.findByRequiresManualReviewTrueAndDocumentType(parseType(type));
        return pending.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public AdminDocumentResponse get(@PathVariable String id) {
        return toResponse(documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found.")));
    }

    @PostMapping("/{id}/approve")
    public AdminDocumentResponse approve(@RequestHeader("Authorization") String authorization,
            @PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String adminId = sessions.requireAdmin(authorization).userId();
        String notes = body == null ? null : body.get("notes");
        return toResponse(verification.approveDocument(id, adminId, notes));
    }

    @PostMapping("/{id}/reject")
    public AdminDocumentResponse reject(@RequestHeader("Authorization") String authorization,
            @PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String adminId = sessions.requireAdmin(authorization).userId();
        String reason = body == null ? null : body.get("reason");
        return toResponse(verification.rejectDocument(id, adminId, reason));
    }

    private DocumentType parseType(String type) {
        try {
            return DocumentType.valueOf(type.toUpperCase().trim());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document type.");
        }
    }

    private AdminDocumentResponse toResponse(UserDocument doc) {
        Map<String, String> qr = new LinkedHashMap<>();
        documents.findByUserIdAndDocumentType(doc.getUserId(), DocumentType.AADHAAR)
                .map(UserDocument::getQrData)
                .ifPresent(data -> {
                    putIfPresent(qr, "name", data.getName());
                    putIfPresent(qr, "dob", data.getDob());
                    putIfPresent(qr, "gender", data.getGender());
                    putIfPresent(qr, "address", data.getAddress());
                    putIfPresent(qr, "pincode", data.getPincode());
                    putIfPresent(qr, "aadhaarLast4", data.getAadhaarLast4());
                });
        List<CloudinaryRef> images = doc.getCloudinaryReferences() == null ? List.of()
                : doc.getCloudinaryReferences().stream().map(ref -> {
                    String publicId = ref.getPublicId();
                    if (publicId != null && !publicId.isBlank()) {
                        return new CloudinaryRef(publicId, cloudinaryService.getSignedUrl(publicId), ref.getLabel());
                    }
                    return ref;
                }).toList();

        String panPhotoUrl = doc.getPanQrPhotoUrl();
        if (doc.getPanQrPhotoPublicId() != null && !doc.getPanQrPhotoPublicId().isBlank()) {
            panPhotoUrl = cloudinaryService.getSignedUrl(doc.getPanQrPhotoPublicId());
        }

        return new AdminDocumentResponse(
                doc.getId(), doc.getUserId(), doc.getDocumentType(), doc.getVerificationStatus(),
                doc.getMaskedDocumentNumber(), doc.getPanNumber(),
                doc.getExtractedData() == null ? Map.of() : doc.getExtractedData(),
                doc.getCrossDocumentMismatchFields(),
                Boolean.TRUE.equals(doc.getRequiresManualReview()),
                doc.getManualReviewReason(), doc.getFailureReason(),
                doc.getReviewedBy(), doc.getReviewedAt(), doc.getReviewDecision(), doc.getReviewNotes(),
                images,
                qr, doc.getPanQrData(), Boolean.TRUE.equals(doc.getIsPanQrVerified()),
                panPhotoUrl, doc.getCreatedAt(), doc.getVerifiedAt());
    }

    private void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }
}
