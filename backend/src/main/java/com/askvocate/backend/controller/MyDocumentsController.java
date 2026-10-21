package com.askvocate.backend.controller;

import com.askvocate.backend.entity.DocType;
import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.*;
import com.askvocate.backend.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Account-scoped document catalogue, upload and image delivery for the mobile app. */
@RestController
@RequestMapping("/api/my-documents")
public class MyDocumentsController {
    private static final Logger log = LoggerFactory.getLogger(MyDocumentsController.class);
    private final AppSessionService sessions;
    private final UserDocumentRepository verifiedDocs;
    private final UserDocRepository uploadedDocs;
    private final SelfieVerificationRepository selfies;
    private final DocumentVerificationService verifier;
    private final DocumentService uploads;
    private final CloudinaryService cloudinary;

    public MyDocumentsController(AppSessionService sessions, UserDocumentRepository verifiedDocs,
            UserDocRepository uploadedDocs, SelfieVerificationRepository selfies,
            DocumentVerificationService verifier, DocumentService uploads, CloudinaryService cloudinary) {
        this.sessions = sessions;
        this.verifiedDocs = verifiedDocs;
        this.uploadedDocs = uploadedDocs;
        this.selfies = selfies;
        this.verifier = verifier;
        this.uploads = uploads;
        this.cloudinary = cloudinary;
    }

    public record Item(String type, String title, String status, String note, String maskedNumber,
            Map<String, String> details, Instant submittedAt, Instant verifiedAt,
            List<String> media, boolean requiresBack, boolean uploadOnly, boolean liveCapture,
            String aadhaarNumber, String panNumber,
            Map<String, String> qrDetails, String qrPhotoUrl) {}

    private List<String> types(String role) {
        if ("CLIENT".equals(role)) return List.of();
        return List.of("AADHAAR", "PAN", "BAR_COUNCIL_ID", "BAR_CERTIFICATE",
                "CERTIFICATE_OF_PRACTICE", "DEGREE_CERTIFICATE", "AIBE_CERTIFICATE", "SELFIE");
    }

    private String title(String type) {
        return switch (type) {
            case "AADHAAR" -> "Aadhaar Card";
            case "PAN" -> "PAN Card";
            case "BAR_COUNCIL_ID" -> "Bar Council ID";
            case "BAR_CERTIFICATE" -> "Bar Enrollment Certificate";
            case "CERTIFICATE_OF_PRACTICE" -> "Certificate of Practice";
            case "DEGREE_CERTIFICATE" -> "Law Degree Certificate";
            case "AIBE_CERTIFICATE" -> "AIBE Certificate";
            default -> "Live Selfie";
        };
    }

    private void requireLawyer(AppSessionService.Account account) {
        if (!"LAWYER_FRESHER".equals(account.role()) && !"LAWYER_EXPERIENCED".equals(account.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Document verification is only available for lawyer accounts.");
    }

    private void allow(AppSessionService.Account account, String type) {
        requireLawyer(account);
        if (!types(account.role()).contains(type))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document type unavailable for this account");
    }

    @GetMapping
    public List<Item> list(@RequestHeader("Authorization") String authorization) {
        var account = sessions.requireAccount(authorization);
        requireLawyer(account);
        log.info("My documents list requested for role={}", account.role());
        List<Item> items = types(account.role()).stream().map(type -> item(account.userId(), type, false)).toList();
        log.info("My documents list returned: role={}, count={}", account.role(), items.size());
        return items;
    }

    @GetMapping("/{type}")
    public ResponseEntity<Item> detail(@RequestHeader("Authorization") String authorization, @PathVariable String type) {
        var account = sessions.requireAccount(authorization);
        allow(account, type);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(item(account.userId(), type, true));
    }

    @GetMapping("/{type}/number")
    public ResponseEntity<Map<String, String>> revealNumber(@RequestHeader("Authorization") String authorization,
            @PathVariable String type) {
        var account = sessions.requireAccount(authorization);
        allow(account, type);
        if ("AADHAAR".equals(type)) {
            var document = verifiedDocs.findByUserIdAndDocumentType(account.userId(), DocumentType.AADHAAR)
                    .filter(doc -> doc.getVerificationStatus() == VerificationStatus.VERIFIED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verified Aadhaar not found"));
            String number = document.getAadhaarNumber();
            if (number == null || number.isBlank())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aadhaar number is unavailable");
            log.info("Aadhaar number reveal requested for verified account document");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("aadhaarNumber", number));
        }
        if ("PAN".equals(type)) {
            var document = verifiedDocs.findByUserIdAndDocumentType(account.userId(), DocumentType.PAN)
                    .filter(doc -> doc.getVerificationStatus() == VerificationStatus.VERIFIED)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verified PAN not found"));
            String number = document.getPanNumber();
            if (number == null || number.isBlank())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PAN number is unavailable");
            log.info("PAN number reveal requested for verified account document");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("panNumber", number));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Number reveal is only available for Aadhaar and PAN");
    }

    @PostMapping(value = "/PAN/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Item scanPanQr(@RequestHeader("Authorization") String authorization,
            @RequestPart("image") MultipartFile image) {
        var account = sessions.requireAccount(authorization);
        allow(account, "PAN");
        checkImage(image);
        verifier.scanPanQr(account.userId(), image);
        return item(account.userId(), "PAN", true);
    }

    public record PanQrTextRequest(String qrText) {}

    @PostMapping(value = "/PAN/qr-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Item scanPanQrText(@RequestHeader("Authorization") String authorization,
            @RequestBody PanQrTextRequest request) {
        var account = sessions.requireAccount(authorization);
        allow(account, "PAN");
        if (request == null || request.qrText() == null || request.qrText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The scanned PAN QR is empty.");
        }
        verifier.scanPanQrText(account.userId(), request.qrText().trim());
        return item(account.userId(), "PAN", true);
    }

    private Item item(String userId, String type, boolean includeSensitiveNumbers) {
        if ("SELFIE".equals(type)) {
            var latest = selfies.findByUserIdOrderByCreatedAtDesc(userId).stream().findFirst();
            if (latest.isEmpty()) return new Item(type, title(type), "NOT_UPLOADED", "Complete the live camera check.",
                    null, Map.of(), null, null, List.of(), false, false, true, null, null, Map.of(), null);
            var selfie = latest.get();
            String status = selfie.getVerificationStatus() == null ? "PENDING" : selfie.getVerificationStatus().name();
            List<String> media = selfie.getSelfieImageUrl() == null ? List.of() : List.of("selfie");
            return new Item(type, title(type), status,
                    "VERIFIED".equals(status) ? null : cleanNote(selfie.getFailureReason()), null, Map.of(),
                    selfie.getCreatedAt(), selfie.getVerifiedAt(), media, false, false, true, null, null, Map.of(), null);
        }
        if ("DEGREE_CERTIFICATE".equals(type) || "AIBE_CERTIFICATE".equals(type)) {
            var latest = uploadedDocs.findByUserIdAndDocType(userId, DocType.valueOf(type));
            if (latest.isEmpty()) return new Item(type, title(type), "NOT_UPLOADED",
                    "Upload available. Automatic verification is not available yet.", null,
                    Map.of(), null, null, List.of(), false, true, false, null, null, Map.of(), null);
            var doc = latest.get();
            return new Item(type, title(type), "UPLOADED", "Uploaded; verification is not available yet.",
                    null, Map.of(), Instant.ofEpochMilli(doc.getUploadedAt()), null,
                    doc.getFileUrl() == null ? List.of() : List.of("front"), false, true, false, null, null, Map.of(), null);
        }
        var found = verifiedDocs.findByUserIdAndDocumentType(userId, DocumentType.valueOf(type));
        if (found.isEmpty()) return new Item(type, title(type), "NOT_UPLOADED", null, null,
                Map.of(), null, null, List.of(), "AADHAAR".equals(type), false, false, null, null, Map.of(), null);
        var doc = found.get();
        List<String> media = doc.getCloudinaryReferences() == null ? List.of() : doc.getCloudinaryReferences()
                .stream().map(CloudinaryRef::getLabel).filter(Objects::nonNull).distinct().toList();
        String note = doc.getVerificationStatus() == VerificationStatus.VERIFIED
                ? null : cleanNote(doc.getFailureReason());
        return new Item(type, title(type), doc.getVerificationStatus().name(), note,
                doc.getMaskedDocumentNumber(), doc.getExtractedData() == null ? Map.of() : doc.getExtractedData(),
                doc.getCreatedAt(), doc.getVerifiedAt(), media, "AADHAAR".equals(type), false, false,
                includeSensitiveNumbers && "AADHAAR".equals(type)
                        && doc.getVerificationStatus() == VerificationStatus.VERIFIED
                        ? doc.getAadhaarNumber() : null,
                includeSensitiveNumbers && "PAN".equals(type)
                        && doc.getVerificationStatus() == VerificationStatus.VERIFIED
                        ? doc.getPanNumber() : null,
                qrDetails(doc), qrPhotoUrl(doc));
    }

    private String qrPhotoUrl(UserDocument doc) {
        QrData qr = doc.getQrData();
        if (qr != null && qr.getPhotoUrl() != null) return qr.getPhotoUrl();
        return doc.getDocumentType() == DocumentType.PAN ? doc.getPanQrPhotoUrl() : null;
    }

    /** Verified Aadhaar details are displayed from the signed QR, not noisy OCR text. */
    private Map<String, String> qrDetails(UserDocument doc) {
        QrData qr = doc.getQrData();
        if (qr == null || doc.getVerificationStatus() != VerificationStatus.VERIFIED) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "Name", qr.getName());
        putIfPresent(values, "Date of Birth", qr.getDob());
        // OCR already validates gender against the signed QR; its full words read better than M/F/T.
        String gender = doc.getExtractedData() != null ? doc.getExtractedData().get("gender") : null;
        putIfPresent(values, "Gender", gender != null && !gender.isBlank() ? gender : qr.getGender());
        putIfPresent(values, "Address", qr.getAddress());
        putIfPresent(values, "Pincode", qr.getPincode());
        return values;
    }

    private void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }

    private String cleanNote(String note) {
        return note == null || note.isBlank() || "null".equalsIgnoreCase(note.trim()) ? null : note;
    }

    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Item upload(@RequestHeader("Authorization") String authorization, @PathVariable String type,
            @RequestPart("front") MultipartFile front,
            @RequestPart(value = "back", required = false) MultipartFile back) throws Exception {
        var account = sessions.requireAccount(authorization);
        allow(account, type);
        if ("SELFIE".equals(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use live selfie capture");
        checkImage(front);
        if (back != null) checkImage(back);
        long startedAt = System.nanoTime();
        long backBytes = back == null ? 0 : back.getSize();
        log.info("Document upload started: role={}, type={}, frontBytes={}, backBytes={}",
                account.role(), type, front.getSize(), backBytes);
        try {
            if ("DEGREE_CERTIFICATE".equals(type) || "AIBE_CERTIFICATE".equals(type)) {
                uploads.uploadDocument(account.userId(), DocType.valueOf(type), front);
            } else {
                verifier.verifyDocument(account.userId(), DocumentType.valueOf(type), front, back);
            }
            Item result = item(account.userId(), type, false);
            log.info("Document upload finished: type={}, status={}, durationMs={}", type, result.status(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return result;
        } catch (Exception error) {
            log.error("Document upload failed: role={}, type={}, frontBytes={}, backBytes={}, durationMs={}, cause={}",
                    account.role(), type, front.getSize(), backBytes,
                    (System.nanoTime() - startedAt) / 1_000_000, error.getClass().getSimpleName(), error);
            throw error;
        }
    }

    private void checkImage(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > DocumentImageCompressor.MAX_INPUT_BYTES || file.getContentType() == null
                || !Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType())) {
            log.warn("Document image rejected: bytes={}, contentType={}", file.getSize(), file.getContentType());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a JPG, PNG or WebP image up to 30 MB");
        }
    }

    @GetMapping(value = "/{type}/media/{side}")
    public ResponseEntity<byte[]> media(@RequestHeader("Authorization") String authorization,
            @PathVariable String type, @PathVariable String side) throws IOException {
        var account = sessions.requireAccount(authorization);
        allow(account, type);
        CloudinaryRef ref;
        if ("SELFIE".equals(type)) {
            if (!"selfie".equals(side)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            var doc = selfies.findByUserIdOrderByCreatedAtDesc(account.userId()).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            ref = new CloudinaryRef(doc.getSelfiePublicId(), doc.getSelfieImageUrl(), side);
        } else if ("DEGREE_CERTIFICATE".equals(type) || "AIBE_CERTIFICATE".equals(type)) {
            if (!"front".equals(side)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            var doc = uploadedDocs.findByUserIdAndDocType(account.userId(), DocType.valueOf(type))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            ref = new CloudinaryRef(null, doc.getFileUrl(), side);
        } else {
            var doc = verifiedDocs.findByUserIdAndDocumentType(account.userId(), DocumentType.valueOf(type))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            ref = doc.getCloudinaryReferences().stream().filter(r -> side.equals(r.getLabel())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        byte[] bytes = cloudinary.downloadReference(ref);
        MediaType contentType = imageMediaType(bytes);
        log.info("Document media delivered: type={}, side={}, bytes={}", type, side, bytes.length);
        return ResponseEntity.ok().contentType(contentType).cacheControl(CacheControl.noStore()).body(bytes);
    }

    private MediaType imageMediaType(byte[] bytes) {
        if (bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P') return MediaType.IMAGE_PNG;
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
