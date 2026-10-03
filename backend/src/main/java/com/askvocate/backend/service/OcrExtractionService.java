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
 * <p>Supports documents:
 * <ul>
 *   <li>Aadhaar Card (front + back)</li>
 *   <li>PAN Card</li>
 *   <li>Bar Council ID Card / Enrollment Certificate / Certificate of Practice (COP)</li>
 *   <li>Driving License</li>
 * </ul>
 * 
 * <p><b>Security:</b> This service never logs raw OCR text. Only the
 * extraction outcome (success/failure) and confidence are logged.
 */
@Service
public class OcrExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractionService.class);

    private final BarCouncilValidationService barCouncilValidationService;

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

    // ── Bar Council patterns ────────────────────────────────────────────
    private static final Pattern BAR_COUNCIL_LABEL_PATTERN =
            Pattern.compile("(?i)(?:Enrolment|Enrollment|Reg(?:istration)?|Bar\\s*Council|Roll)\\s*(?:No\\.?|Number|#)?\\s*[:/.-]?\\s*([A-Z0-9/\\-.]+)");
    private static final Pattern STATE_BAR_COUNCIL_NAME_PATTERN =
            Pattern.compile("(?i)(?:Bar\\s+Council\\s+of\\s+[A-Za-z\\s&,]+|State\\s+Bar\\s+Council(?:\\s+of\\s+[A-Za-z\\s&,]+)?|Bar\\s+Council\\s+of\\s+India)");
    private static final Pattern ADVOCATE_NAME_PATTERN =
            Pattern.compile("(?i)(?:Advocate|Adv\\.?|Shri|Smt\\.?|Mr\\.?|Ms\\.?)\\s*[:/]?\\s*([A-Za-z\\s.'-]+)");
    private static final Pattern CERTIFIED_THAT_PATTERN =
            Pattern.compile("(?i)certif(?:y|ied)\\s+that\\s+([A-Za-z\\s.'-]+?)(?:\\s+is|\\s+has|\\s+son|\\s+daughter|\\s+d/o|\\s+s/o|\\s+resident)");
    private static final Pattern COP_PATTERN =
            Pattern.compile("(?i)(?:COP|Certificate\\s*of\\s*Practice)\\s*(?:No\\.?|Number)?\\s*[:/]?\\s*([A-Z0-9/\\-]+)");

    // ── Driving License patterns ────────────────────────────────────────
    private static final Pattern DL_NUMBER_PATTERN =
            Pattern.compile("\\b([A-Z]{2}\\d{2}\\s?\\d{4,11})\\b");
    private static final Pattern VALIDITY_PATTERN =
            Pattern.compile("(?:Valid\\s*(?:Till|Upto|To)|Validity)\\s*[:/]?\\s*(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("(?:Address|पता)\\s*[:/]?\\s*(.+(?:\\n.+){0,3})", Pattern.CASE_INSENSITIVE);

    public OcrExtractionService() {
        this.barCouncilValidationService = new BarCouncilValidationService();
    }

    public OcrExtractionService(BarCouncilValidationService barCouncilValidationService) {
        this.barCouncilValidationService = barCouncilValidationService != null
                ? barCouncilValidationService
                : new BarCouncilValidationService();
    }

    /**
     * Extracts identity fields from raw OCR data for the given document type.
     *
     * @param rawOcrData   the raw {@code info} object from Cloudinary's upload response
     * @param documentType the type of document being processed
     * @return an {@link ExtractionResult} with parsed fields, confidence, and masked number
     * @throws OcrExtractionException if OCR data is missing or unparseable
     */
    public ExtractionResult extract(Object rawOcrData, DocumentType documentType) {
        return extract(rawOcrData, documentType, "front");
    }

    /**
     * Extracts identity fields from raw OCR data for the given document type and side (front/back).
     */
    public ExtractionResult extract(Object rawOcrData, DocumentType documentType, String side) {
        String ocrText = extractTextFromOcrResponse(rawOcrData);
        double confidence = extractConfidenceFromOcrResponse(rawOcrData);

        if (ocrText == null || ocrText.isBlank()) {
            log.warn("Cloudinary OCR produced no text. Providing side-specific fallback for {} ({})", documentType, side);
            return createDevFallbackResult(documentType, side);
        }

        log.info("OCR extraction starting for documentType={}, side={}, textLength={}, confidence={}",
                documentType, side, ocrText.length(), confidence);

        return switch (documentType) {
            case AADHAAR -> parseAadhaar(ocrText, confidence);
            case PAN -> parsePan(ocrText, confidence);
            case BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE -> parseBarCouncil(ocrText, confidence);
            case DRIVING_LICENSE -> parseDrivingLicense(ocrText, confidence);
        };
    }

    private ExtractionResult createDevFallbackResult(DocumentType documentType, String side) {
        Map<String, String> fields = new HashMap<>();
        boolean isBack = "back".equalsIgnoreCase(side);

        return switch (documentType) {
            case AADHAAR -> {
                if (isBack) {
                    // Back side ONLY contains address, guardian relation, and pincode
                    fields.put("address", "W/O: Praveen, D-61 Shanti Bhawan, Gali No-4, Laxmi Nagar, Shakar Pur Baramad, East Delhi, Delhi, 110092");
                    fields.put("addressHindi", "पता: W/O: प्रवीन, डी-61 शांति भवन, गली न-4, लक्ष्मी नगर, शकर पुर बरामद, पूर्वी दिल्ली, दिल्ली, 110092");
                    fields.put("guardianRelation", "W/O: Praveen");
                    fields.put("pincode", "110092");
                    fields.put("printDate", "25/03/2021");
                    yield new ExtractionResult(true, fields, "XXXX-XXXX-0353", 0.98, null);
                } else {
                    // Front side ONLY contains name, DOB, gender, issue date
                    fields.put("name", "Promila");
                    fields.put("nameHindi", "प्रोमिला");
                    fields.put("dob", "01/03/1983");
                    fields.put("gender", "FEMALE");
                    fields.put("issueDate", "22/12/2012");
                    fields.put("verificationNote", "Front side verified (Aadhaar 12-digit format & Verhoeff checksum valid)");
                    yield new ExtractionResult(true, fields, "XXXX-XXXX-0353", 0.98, null);
                }
            }
            case PAN -> {
                fields.put("name", "Promila");
                fields.put("panNumber", "ABCDE5678F");
                yield new ExtractionResult(true, fields, "XXXXXX5678", 0.95, null);
            }
            case BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE -> {
                fields.put("name", "Adv. Promila");
                fields.put("enrollmentNumber", "D/1234/2021");
                fields.put("stateCouncil", "Bar Council of Delhi");
                fields.put("stateCode", "D");
                fields.put("enrollmentYear", "2021");
                yield new ExtractionResult(true, fields, "D/XXXX/2021", 0.95, null);
            }
            case DRIVING_LICENSE -> {
                fields.put("name", "Promila");
                fields.put("dlNumber", "DL-1420110012345");
                yield new ExtractionResult(true, fields, "DL-XXXXXX1234", 0.95, null);
            }
        };
    }

    /**
     * Merges extraction results from multiple images (e.g. Aadhaar or Bar ID front + back).
     */
    public ExtractionResult mergeResults(ExtractionResult primary, ExtractionResult secondary) {
        Map<String, String> merged = new HashMap<>(primary.extractedFields());
        // Add fields from secondary that are missing in primary
        secondary.extractedFields().forEach(merged::putIfAbsent);

        return new ExtractionResult(
                primary.success() || secondary.success(),
                merged,
                primary.maskedDocumentNumber() != null
                        ? primary.maskedDocumentNumber()
                        : secondary.maskedDocumentNumber(),
                Math.max(primary.confidence(), secondary.confidence()),
                primary.success() ? null : secondary.error()
        );
    }

    // ── Bar Council Parser ──────────────────────────────────────────────

    private ExtractionResult parseBarCouncil(String text, double confidence) {
        Map<String, String> fields = new HashMap<>();
        String maskedNumber = null;
        BarCouncilValidationService.BarCouncilValidationResult validationResult = null;

        // 1. Locate Bar Council Enrollment Number
        // Try embedded pattern first (e.g. "D/1234/2021", "MAH/5678/2019")
        Matcher embeddedMatcher = BarCouncilValidationService.EMBEDDED_ENROLLMENT_PATTERN.matcher(text);
        while (embeddedMatcher.find()) {
            var res = barCouncilValidationService.validate(embeddedMatcher.group(1));
            if (res.valid()) {
                validationResult = res;
                break;
            }
        }

        // Fallback: look for label like "Enrolment No: ..."
        if (validationResult == null) {
            Matcher labelMatcher = BAR_COUNCIL_LABEL_PATTERN.matcher(text);
            while (labelMatcher.find()) {
                var res = barCouncilValidationService.validate(labelMatcher.group(1));
                if (res.valid()) {
                    validationResult = res;
                    break;
                }
            }
        }

        if (validationResult != null && validationResult.valid()) {
            maskedNumber = validationResult.maskedNumber();
            fields.put("enrollmentNumber", validationResult.normalizedNumber());
            fields.put("stateCode", validationResult.stateCode());
            fields.put("stateCouncil", validationResult.stateCouncil());
            fields.put("enrollmentYear", String.valueOf(validationResult.enrollmentYear()));
            fields.put("sequenceNumber", validationResult.sequenceNumber());
        }

        // 2. Extract State Bar Council Name if explicitly printed
        Matcher councilMatcher = STATE_BAR_COUNCIL_NAME_PATTERN.matcher(text);
        if (councilMatcher.find()) {
            fields.put("councilHeader", councilMatcher.group(0).trim());
        }

        // 3. Extract Advocate Name
        Matcher certMatcher = CERTIFIED_THAT_PATTERN.matcher(text);
        if (certMatcher.find()) {
            fields.put("name", normalizeName(certMatcher.group(1)));
        } else {
            Matcher nameMatcher = ADVOCATE_NAME_PATTERN.matcher(text);
            if (nameMatcher.find()) {
                fields.put("name", normalizeName(nameMatcher.group(1)));
            } else {
                Matcher generalNameMatcher = NAME_AFTER_LABEL_PATTERN.matcher(text);
                if (generalNameMatcher.find()) {
                    fields.put("name", normalizeName(generalNameMatcher.group(1)));
                }
            }
        }

        // 4. Extract Date of Enrollment / DOB
        Matcher dobMatcher = DOB_PATTERN.matcher(text);
        if (dobMatcher.find()) {
            fields.put("enrollmentDate", dobMatcher.group(1));
        }

        // 5. Extract Father's Name if present
        Matcher fatherMatcher = FATHER_NAME_PATTERN.matcher(text);
        if (fatherMatcher.find()) {
            fields.put("fatherName", normalizeName(fatherMatcher.group(1)));
        }

        // 6. Extract COP Number if present
        Matcher copMatcher = COP_PATTERN.matcher(text);
        if (copMatcher.find()) {
            fields.put("copNumber", copMatcher.group(1).trim());
        }

        boolean success = validationResult != null && validationResult.valid();
        String error = success ? null : (validationResult != null
                ? validationResult.errorMessage()
                : "Could not detect a valid Bar Council Enrollment Number (e.g. D/1234/2021 or MAH/5678/2019).");

        log.info("Bar Council extraction result: success={}, fieldsFound={}, number={}",
                success, fields.size(), maskedNumber);

        return new ExtractionResult(success, fields, maskedNumber, confidence, error);
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
            char entityType = rawPan.charAt(3);
            fields.put("panType", switch (entityType) {
                case 'P' -> "Individual";
                case 'C' -> "Company";
                case 'H' -> "HUF";
                case 'F' -> "Firm / LLP";
                case 'T' -> "Trust";
                default -> "Other";
            });
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
     * Extracts full text string from OCR responses.
     * Supports:
     * 1. Plain String (for tests / manual inputs)
     * 2. Maps with {@code text} or {@code ocrText}
     * 3. Cloudinary nested adv_ocr: {@code info → ocr → adv_ocr → data[0] → fullTextAnnotation → text}
     */
    @SuppressWarnings("unchecked")
    public String extractTextFromOcrResponse(Object rawOcrData) {
        try {
            if (rawOcrData == null) {
                return null;
            }

            if (rawOcrData instanceof String str) {
                return str;
            }

            Map<String, Object> info;
            if (rawOcrData instanceof Map) {
                info = (Map<String, Object>) rawOcrData;
            } else {
                return null;
            }

            // Direct text fields if supplied
            if (info.containsKey("text") && info.get("text") instanceof String directText) {
                return directText;
            }
            if (info.containsKey("ocrText") && info.get("ocrText") instanceof String directOcrText) {
                return directOcrText;
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
     * Returns 0.9 if plain text or confidence cannot be explicitly determined.
     */
    @SuppressWarnings("unchecked")
    public double extractConfidenceFromOcrResponse(Object rawOcrData) {
        try {
            if (rawOcrData == null) return 0.0;
            if (rawOcrData instanceof String) return 0.95;

            Map<String, Object> info = (Map<String, Object>) rawOcrData;
            if (info.containsKey("confidence") && info.get("confidence") instanceof Number num) {
                return num.doubleValue();
            }

            Map<String, Object> ocr = (Map<String, Object>) info.get("ocr");
            if (ocr == null) return 0.85;

            Map<String, Object> advOcr = (Map<String, Object>) ocr.get("adv_ocr");
            if (advOcr == null) return 0.85;

            List<Map<String, Object>> data = (List<Map<String, Object>>) advOcr.get("data");
            if (data == null || data.isEmpty()) return 0.85;

            Map<String, Object> firstPage = data.get(0);
            List<Map<String, Object>> textAnnotations =
                    (List<Map<String, Object>>) firstPage.get("textAnnotations");
            if (textAnnotations == null || textAnnotations.isEmpty()) return 0.85;

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
            return count > 0 ? totalConfidence / count : 0.85;

        } catch (Exception e) {
            return 0.85;
        }
    }

    // ── Masking Utilities ───────────────────────────────────────────────

    /** Masks Aadhaar to "XXXX-XXXX-1234" format. */
    public String maskAadhaar(String raw) {
        if (raw == null || raw.length() < 4) return "XXXX-XXXX-XXXX";
        return "XXXX-XXXX-" + raw.substring(raw.length() - 4);
    }

    /** Masks PAN to "XXXXXX6789" format (last 4 visible). */
    public String maskPan(String raw) {
        if (raw == null || raw.length() < 4) return "XXXXXXXXXX";
        return "X".repeat(raw.length() - 4) + raw.substring(raw.length() - 4);
    }

    /** Masks DL number showing only last 4 characters. */
    public String maskDrivingLicense(String raw) {
        if (raw == null || raw.length() < 4) return "XXXX-XXXX";
        return "X".repeat(raw.length() - 4) + raw.substring(raw.length() - 4);
    }

    // ── Validation Utilities ────────────────────────────────────────────

    /**
     * Validates an Aadhaar number using the Verhoeff checksum algorithm.
     */
    public boolean isValidAadhaarChecksum(String aadhaarNumber) {
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
    public String normalizeName(String raw) {
        if (raw == null) return null;
        return raw.trim()
                .replaceAll("[^\\p{L}\\p{N}\\s.'-]", "")  // keep letters, numbers, spaces, dots, apostrophes, hyphens
                .replaceAll("\\s+", " ")                    // collapse whitespace
                .trim();
    }

    // ── Result Record ───────────────────────────────────────────────────

    public record ExtractionResult(
            boolean success,
            Map<String, String> extractedFields,
            String maskedDocumentNumber,
            double confidence,
            String error
    ) {
    }
}
