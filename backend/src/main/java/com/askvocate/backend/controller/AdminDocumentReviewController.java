package com.askvocate.backend.controller;

import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.UserDocumentRepository;
import com.askvocate.backend.service.AppSessionService;
import com.askvocate.backend.service.S3StorageService;
import com.askvocate.backend.service.DocumentVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/** Admin-only document review queue for every document type that requires manual review. */
@RestController
@RequestMapping("/api/admin/verification/documents")
public class AdminDocumentReviewController {

    private final DocumentVerificationService verification;
    private final UserDocumentRepository documents;
    private final AppSessionService sessions;
    private final S3StorageService storageService;

    public AdminDocumentReviewController(DocumentVerificationService verification,
            UserDocumentRepository documents, AppSessionService sessions,
            S3StorageService storageService) {
        this.verification = verification;
        this.documents = documents;
        this.sessions = sessions;
        this.storageService = storageService;
    }

    public record AdminDocumentResponse(
            String id, String userId, DocumentType documentType,
            VerificationStatus verificationStatus, String maskedDocumentNumber, String panNumber,
            Map<String, String> extractedData,
            List<String> crossDocumentMismatchFields, List<String> verificationFlags,
            String institutionCanonicalName, String institutionMatchType,
            Double institutionMatchScore, Boolean courseApproved, Boolean approvalValid,
            String bciRegistryVersion,
            List<ReviewChecklistItem> reviewChecklistItems, Map<String, Boolean> reviewChecklist,
            Boolean requiresManualReview,
            String manualReviewReason, String failureReason, String reviewedBy,
            Instant reviewedAt, String reviewDecision, String reviewNotes,
            List<StorageRef> images, Map<String, String> aadhaarQrData,
            Map<String, String> panQrData, Boolean isPanQrVerified, String panQrPhotoUrl,
            Instant submittedAt, Instant verifiedAt) {}

    public record ReviewChecklistItem(String key, String label) {}

    @GetMapping("/pending")
    public List<AdminDocumentResponse> pending(
            @RequestParam(value = "type", required = false) String type) {
        List<UserDocument> pending = type == null || type.isBlank()
                ? documents.findByRequiresManualReviewTrue()
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
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String adminId = sessions.requireAdmin(authorization).userId();
        return toResponse(verification.approveDocument(id, adminId,
                stringValue(body, "notes"), checklist(body)));
    }

    @PostMapping("/{id}/reject")
    public AdminDocumentResponse reject(@RequestHeader("Authorization") String authorization,
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String adminId = sessions.requireAdmin(authorization).userId();
        return toResponse(verification.rejectDocument(id, adminId, stringValue(body, "reason")));
    }

    private String stringValue(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** Accepts {@code {"checklist": {"degree_is_llb": true, ...}}}. */
    private Map<String, Boolean> checklist(Map<String, Object> body) {
        if (body == null || !(body.get("checklist") instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Boolean> values = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) values.put(String.valueOf(key), Boolean.parseBoolean(String.valueOf(value)));
        });
        return values;
    }

    private List<ReviewChecklistItem> reviewChecklistItems(DocumentType type) {
        if (type != DocumentType.DEGREE_CERTIFICATE) return List.of();
        return List.of(
                new ReviewChecklistItem("degree_is_llb", "Degree is LL.B. / B.A. LL.B."),
                new ReviewChecklistItem("final_or_provisional",
                        "Document is Final or Provisional (with seal & signature)."),
                new ReviewChecklistItem("name_matches_kyc", "Name matches KYC."),
                new ReviewChecklistItem("university_bci_recognized", "University is BCI-recognized."),
                new ReviewChecklistItem("course_bci_recognized",
                        "Course and approval year are BCI-recognized."),
                new ReviewChecklistItem("foreign_equivalence",
                        "(If Foreign) BCI Equivalence Certificate provided.")
        );
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
        List<StorageRef> images = doc.getStorageReferences() == null ? List.of()
                : doc.getStorageReferences().stream().map(ref -> {
                    String publicId = ref.getPublicId();
                    if (publicId != null && !publicId.isBlank()) {
                        return new StorageRef(publicId, storageService.getSignedUrl(publicId), ref.getLabel());
                    }
                    return ref;
                }).toList();

        String panPhotoUrl = doc.getPanQrPhotoUrl();
        if (doc.getPanQrPhotoPublicId() != null && !doc.getPanQrPhotoPublicId().isBlank()) {
            panPhotoUrl = storageService.getSignedUrl(doc.getPanQrPhotoPublicId());
        }

        return new AdminDocumentResponse(
                doc.getId(), doc.getUserId(), doc.getDocumentType(), doc.getVerificationStatus(),
                doc.getMaskedDocumentNumber(), doc.getPanNumber(),
                doc.getExtractedData() == null ? Map.of() : doc.getExtractedData(),
                doc.getCrossDocumentMismatchFields(), doc.getVerificationFlags(),
                doc.getInstitutionCanonicalName(), doc.getInstitutionMatchType(),
                doc.getInstitutionMatchScore(), doc.getCourseApproved(), doc.getApprovalValid(),
                doc.getBciRegistryVersion(),
                reviewChecklistItems(doc.getDocumentType()), doc.getReviewChecklist(),
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
