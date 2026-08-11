package com.askvocate.backend.service;

import com.askvocate.backend.exception.OcrExtractionException;
import com.askvocate.backend.model.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw Cloudinary OCR (adv_ocr) responses into structured identity fields.
 * 
 * <p>Supports three document types: Aadhaar, PAN, and Driving License.
 * Each parser extracts and validates document-specific fields (name, DOB,
 * document number, etc.) using regex pattern matching.
 * 
 * <p><b>Security:</b> This service never logs raw OCR text. Only the
 * extraction outcome (success/failure) and confidence are logged.
 */
@Service
public class OcrExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractionService.class);

    // ── Aadhaar patterns ────────────────────────────────────────────────
    private static final Pattern AADHAAR_NUMBER_PATTERN =
            Pattern.compile("\\b(\\d{4}\\s?\\d{4}\\s?\\d{4})\\b");
    private static final Pattern DOB_PATTERN =
            Pattern.compile("\\b(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4})\\b");
    private static final Pattern GENDER_PATTERN =
            Pattern.compile("\\b(MALE|FEMALE|TRANSGENDER|पुरुष|महिला)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_AFTER_LABEL_PATTERN =
            Pattern.compile("(?:Name|नाम)\\s*[:/]?\\s*(.+)", Pattern.CASE_INSENSITIVE);

    // ── PAN patterns ────────────────────────────────────────────────────
    private static final Pattern PAN_NUMBER_PATTERN =
            Pattern.compile("\\b([A-Z]{5}\\d{4}[A-Z])\\b");
    private static final Pattern FATHER_NAME_PATTERN =
            Pattern.compile("(?:Father'?s?\\s*Name|पिता का नाम)\\s*[:/]?\\s*(.+)", Pattern.CASE_INSENSITIVE);

    // ── Driving License patterns ────────────────────────────────────────
    private static final Pattern DL_NUMBER_PATTERN =
            Pattern.compile("\\b([A-Z]{2}\\d{2}\\s?\\d{4,11})\\b");
    private static final Pattern VALIDITY_PATTERN =
            Pattern.compile("(?:Valid\\s*(?:Till|Upto|To)|Validity)\\s*[:/]?\\s*(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("(?:Address|पता)\\s*[:/]?\\s*(.+(?:\\n.+){0,3})", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts identity fields from raw OCR data for the given document type.
     *
     * @param rawOcrData   the raw {@code info} object from Cloudinary's upload response
     * @param documentType the type of document being processed
     * @return an {@link ExtractionResult} with parsed fields, confidence, and masked number
     * @throws OcrExtractionException if OCR data is missing or unparseable
     */
    public ExtractionResult extract(Object rawOcrData, DocumentType documentType) {
        String ocrText = extractTextFromOcrResponse(rawOcrData);
        double confidence = extractConfidenceFromOcrResponse(rawOcrData);

        if (ocrText == null || ocrText.isBlank()) {
            throw new OcrExtractionException("OCR produced no readable text from the document image.");
        }

        log.info("OCR extraction starting for documentType={}, textLength={}, confidence={}",
                documentType, ocrText.length(), confidence);

        return switch (documentType) {
            case AADHAAR -> parseAadhaar(ocrText, confidence);
            case PAN -> parsePan(ocrText, confidence);
            case DRIVING_LICENSE -> parseDrivingLicense(ocrText, confidence);
        };
    }

    /**
     * Merges extraction results from multiple images (e.g. Aadhaar front + back).
     */
    public ExtractionResult mergeResults(ExtractionResult primary, ExtractionResult secondary) {
        Map<String, String> merged = new HashMap<>(primary.extractedFields());
        // Add fields from secondary that are missing in primary
        secondary.extractedFields().forEach(merged::putIfAbsent);

        return new ExtractionResult(
                true,
                merged,
                primary.maskedDocumentNumber() != null
                        ? primary.maskedDocumentNumber()
                        : secondary.maskedDocumentNumber(),
                Math.max(primary.confidence(), secondary.confidence()),
                null
        );
    }

    // ── Aadhaar Parser ──────────────────────────────────────────────────

    private ExtractionResult parseAadhaar(String text, double confidence) {
        Map<String, String> fields = new HashMap<>();
        String maskedNumber = null;

        // Extract Aadhaar number
        Matcher aadhaarMatcher = AADHAAR_NUMBER_PATTERN.matcher(text);
        if (aadhaarMatcher.find()) {
            String rawNumber = aadhaarMatcher.group(1).replaceAll("\\s", "");
            if (isValidAadhaarChecksum(rawNumber)) {
                maskedNumber = maskAadhaar(rawNumber);
            } else {
                // Still mask it even if checksum fails — it matched the pattern
                maskedNumber = maskAadhaar(rawNumber);
                fields.put("checksumWarning", "Aadhaar checksum validation failed");
            }
        }

        // Extract name
        Matcher nameMatcher = NAME_AFTER_LABEL_PATTERN.matcher(text);
        if (nameMatcher.find()) {
            fields.put("name", normalizeName(nameMatcher.group(1)));
        }

        // Extract DOB
        Matcher dobMatcher = DOB_PATTERN.matcher(text);
        if (dobMatcher.find()) {
            fields.put("dob", dobMatcher.group(1));
        }

        // Extract gender
        Matcher genderMatcher = GENDER_PATTERN.matcher(text);
        if (genderMatcher.find()) {
            fields.put("gender", genderMatcher.group(1).toUpperCase());
        }

        // Extract address (often on back of card)
        Matcher addressMatcher = ADDRESS_PATTERN.matcher(text);
        if (addressMatcher.find()) {
            fields.put("address", addressMatcher.group(1).trim());
        }

        boolean success = maskedNumber != null && fields.containsKey("name");
        String error = success ? null : "Could not extract required Aadhaar fields (number and name).";

        log.info("Aadhaar extraction result: success={}, fieldsFound={}", success, fields.size());

        return new ExtractionResult(success, fields, maskedNumber, confidence, error);
    }

    // ── PAN Parser ──────────────────────────────────────────────────────

    private ExtractionResult parsePan(String text, double confidence) {
        Map<String, String> fields = new HashMap<>();
        String maskedNumber = null;

        // Extract PAN number
        Matcher panMatcher = PAN_NUMBER_PATTERN.matcher(text);
        if (panMatcher.find()) {
            String rawPan = panMatcher.group(1);
            maskedNumber = maskPan(rawPan);
        }

        // Extract name — PAN cards typically have the name after "Name" or in a specific position
        Matcher nameMatcher = NAME_AFTER_LABEL_PATTERN.matcher(text);
        if (nameMatcher.find()) {
            fields.put("name", normalizeName(nameMatcher.group(1)));
        }

        // Extract father's name
        Matcher fatherMatcher = FATHER_NAME_PATTERN.matcher(text);
        if (fatherMatcher.find()) {
            fields.put("fatherName", normalizeName(fatherMatcher.group(1)));
        }

        // Extract DOB
        Matcher dobMatcher = DOB_PATTERN.matcher(text);
        if (dobMatcher.find()) {
            fields.put("dob", dobMatcher.group(1));
        }

        boolean success = maskedNumber != null && fields.containsKey("name");
        String error = success ? null : "Could not extract required PAN fields (number and name).";

        log.info("PAN extraction result: success={}, fieldsFound={}", success, fields.size());

        return new ExtractionResult(success, fields, maskedNumber, confidence, error);
    }

    // ── Driving License Parser ──────────────────────────────────────────

    private ExtractionResult parseDrivingLicense(String text, double confidence) {
        Map<String, String> fields = new HashMap<>();
        String maskedNumber = null;

        // Extract DL number
        Matcher dlMatcher = DL_NUMBER_PATTERN.matcher(text);
        if (dlMatcher.find()) {
            String rawDl = dlMatcher.group(1).replaceAll("\\s", "");
            maskedNumber = maskDrivingLicense(rawDl);
        }

        // Extract name
        Matcher nameMatcher = NAME_AFTER_LABEL_PATTERN.matcher(text);
        if (nameMatcher.find()) {
            fields.put("name", normalizeName(nameMatcher.group(1)));
        }

        // Extract DOB
        Matcher dobMatcher = DOB_PATTERN.matcher(text);
        if (dobMatcher.find()) {
            fields.put("dob", dobMatcher.group(1));
        }

        // Extract validity
        Matcher validityMatcher = VALIDITY_PATTERN.matcher(text);
        if (validityMatcher.find()) {
            fields.put("validTill", validityMatcher.group(1));
        }

        // Extract address
        Matcher addressMatcher = ADDRESS_PATTERN.matcher(text);
        if (addressMatcher.find()) {
            fields.put("address", addressMatcher.group(1).trim());
        }

        boolean success = maskedNumber != null && fields.containsKey("name");
        String error = success ? null : "Could not extract required Driving License fields (number and name).";

        log.info("DL extraction result: success={}, fieldsFound={}", success, fields.size());

        return new ExtractionResult(success, fields, maskedNumber, confidence, error);
    }

    // ── OCR Response Parsing ────────────────────────────────────────────

    /**
     * Extracts the full text string from Cloudinary's nested adv_ocr response structure.
     * 
     * <p>The structure is typically:
     * {@code info → ocr → adv_ocr → data[0] → fullTextAnnotation → text}
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromOcrResponse(Object rawOcrData) {
        try {
            if (rawOcrData == null) {
                return null;
            }

            Map<String, Object> info;
            if (rawOcrData instanceof Map) {
                info = (Map<String, Object>) rawOcrData;
            } else {
                return null;
            }

            Map<String, Object> ocr = (Map<String, Object>) info.get("ocr");
            if (ocr == null) return null;

            Map<String, Object> advOcr = (Map<String, Object>) ocr.get("adv_ocr");
            if (advOcr == null) return null;

            List<Map<String, Object>> data = (List<Map<String, Object>>) advOcr.get("data");
            if (data == null || data.isEmpty()) return null;

            Map<String, Object> firstPage = data.get(0);
            Map<String, Object> fullTextAnnotation =
                    (Map<String, Object>) firstPage.get("fullTextAnnotation");
            if (fullTextAnnotation == null) return null;

            return (String) fullTextAnnotation.get("text");

        } catch (ClassCastException e) {
            log.warn("Unexpected OCR response structure");
            return null;
        }
    }

    /**
     * Extracts confidence score from the OCR response.
     * Returns 0.0 if confidence cannot be determined.
     */
    @SuppressWarnings("unchecked")
    private double extractConfidenceFromOcrResponse(Object rawOcrData) {
        try {
            if (rawOcrData == null) return 0.0;

            Map<String, Object> info = (Map<String, Object>) rawOcrData;
            Map<String, Object> ocr = (Map<String, Object>) info.get("ocr");
            if (ocr == null) return 0.0;

            Map<String, Object> advOcr = (Map<String, Object>) ocr.get("adv_ocr");
            if (advOcr == null) return 0.0;

            List<Map<String, Object>> data = (List<Map<String, Object>>) advOcr.get("data");
            if (data == null || data.isEmpty()) return 0.0;

            Map<String, Object> firstPage = data.get(0);
            List<Map<String, Object>> textAnnotations =
                    (List<Map<String, Object>>) firstPage.get("textAnnotations");
            if (textAnnotations == null || textAnnotations.isEmpty()) return 0.0;

            // Average confidence from text annotations
            double totalConfidence = 0;
            int count = 0;
            for (Map<String, Object> annotation : textAnnotations) {
                Object conf = annotation.get("confidence");
                if (conf instanceof Number) {
                    totalConfidence += ((Number) conf).doubleValue();
                    count++;
                }
            }
            return count > 0 ? totalConfidence / count : 0.0;

        } catch (Exception e) {
            return 0.0;
        }
    }

    // ── Masking Utilities ───────────────────────────────────────────────

    /** Masks Aadhaar to "XXXX-XXXX-1234" format. */
    private String maskAadhaar(String raw) {
        if (raw == null || raw.length() < 4) return "XXXX-XXXX-XXXX";
        return "XXXX-XXXX-" + raw.substring(raw.length() - 4);
    }

    /** Masks PAN to "XXXXXX6789" format (last 4 visible). */
    private String maskPan(String raw) {
        if (raw == null || raw.length() < 4) return "XXXXXXXXXX";
        return "X".repeat(raw.length() - 4) + raw.substring(raw.length() - 4);
    }

    /** Masks DL number showing only last 4 characters. */
    private String maskDrivingLicense(String raw) {
        if (raw == null || raw.length() < 4) return "XXXX-XXXX";
        return "X".repeat(raw.length() - 4) + raw.substring(raw.length() - 4);
    }

    // ── Validation Utilities ────────────────────────────────────────────

    /**
     * Validates an Aadhaar number using the Verhoeff checksum algorithm.
     */
    private boolean isValidAadhaarChecksum(String aadhaarNumber) {
        if (aadhaarNumber == null || aadhaarNumber.length() != 12) {
            return false;
        }

        // Verhoeff multiplication table
        int[][] d = {
            {0,1,2,3,4,5,6,7,8,9}, {1,2,3,4,0,6,7,8,9,5},
            {2,3,4,0,1,7,8,9,5,6}, {3,4,0,1,2,8,9,5,6,7},
            {4,0,1,2,3,9,5,6,7,8}, {5,9,8,7,6,0,4,3,2,1},
            {6,5,9,8,7,1,0,4,3,2}, {7,6,5,9,8,2,1,0,4,3},
            {8,7,6,5,9,3,2,1,0,4}, {9,8,7,6,5,4,3,2,1,0}
        };

        // Verhoeff permutation table
        int[][] p = {
            {0,1,2,3,4,5,6,7,8,9}, {1,5,7,6,2,8,3,0,9,4},
            {5,8,0,3,7,9,6,1,4,2}, {8,9,1,6,0,4,3,5,2,7},
            {9,4,5,3,1,2,6,8,7,0}, {4,2,8,6,5,7,3,9,0,1},
            {2,7,9,3,8,0,6,4,1,5}, {7,0,4,6,9,1,3,2,5,8}
        };

        int c = 0;
        int len = aadhaarNumber.length();
        for (int i = len - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(aadhaarNumber.charAt(i));
            c = d[c][p[(len - i) % 8][digit]];
        }
        return c == 0;
    }

    /** Normalizes a name string: trims whitespace, removes stray punctuation. */
    private String normalizeName(String raw) {
        if (raw == null) return null;
        return raw.trim()
                .replaceAll("[^\\p{L}\\p{N}\\s.'-]", "")  // keep letters, numbers, spaces, dots, apostrophes, hyphens
                .replaceAll("\\s+", " ")                    // collapse whitespace
                .trim();
    }

    // ── Result Record ───────────────────────────────────────────────────

    /**
     * Holds the result of OCR field extraction for a single document.
     *
     * @param success            whether required fields were successfully extracted
     * @param extractedFields    map of field name → value (never contains full doc numbers)
     * @param maskedDocumentNumber masked document number for safe display/storage
     * @param confidence         OCR confidence score (0.0–1.0)
     * @param error              human-readable error message if extraction failed
     */
    public record ExtractionResult(
            boolean success,
            Map<String, String> extractedFields,
            String maskedDocumentNumber,
            double confidence,
            String error
    ) {
    }
}
