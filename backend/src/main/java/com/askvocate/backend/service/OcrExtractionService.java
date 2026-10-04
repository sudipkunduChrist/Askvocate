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
            Pattern.compile("\\b([2-9]\\d{3}(?:[\\s-]{0,2}\\d{4}){2})\\b");
    private static final Pattern AADHAAR_MARKER_PATTERN = Pattern.compile(
            "(?i)\\b(?:aadha{1,2}r|uidai|unique identification authority|government of india)\\b|आधार|भारत सरकार");
    private static final Pattern AADHAAR_DOB_PATTERN = Pattern.compile(
            "(?im)\\b(?:DOB|Date\\s*of\\s*Birth|जन्म\\s*तिथि)\\s*[:/-]?\\s*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})\\b");
    private static final Pattern AADHAAR_YOB_PATTERN = Pattern.compile(
            "(?im)\\b(?:YOB|Year\\s*of\\s*Birth|जन्म\\s*वर्ष)\\s*[:/-]?\\s*((?:19|20)\\d{2})\\b");
    private static final Pattern PINCODE_PATTERN = Pattern.compile("\\b([1-9]\\d{5})\\b");
    private static final Pattern AADHAAR_ENGLISH_ADDRESS_LABEL =
            Pattern.compile("(?i)^[ \\t]*Address[ \\t]*[:：/-]?[ \\t]*(.*)$");
    private static final Pattern AADHAAR_HINDI_ADDRESS_LABEL =
            Pattern.compile("^[ \\t]*पता[ \\t]*[:：/-]?[ \\t]*(.*)$");
    private static final Pattern AADHAAR_ADDRESS_STOP = Pattern.compile(
            "(?i)^[ \\t]*(?:Address|पता|VID|DOB|YOB|Name|Gender|UIDAI|Aadhaar|Unique Identification Authority|www\\.|help@).*|^[2-9]\\d{3}[ \\t-]?\\d{4}[ \\t-]?\\d{4}$");
    private static final Pattern DOB_PATTERN =
            Pattern.compile("\\b(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4})\\b");
    private static final Pattern GENDER_PATTERN =
            Pattern.compile("\\b(MALE|FEMALE|TRANSGENDER|पुरुष|महिला)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_AFTER_LABEL_PATTERN =
            Pattern.compile("(?:Name|नाम)\\s*[:/]?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AADHAAR_NAME_LABEL_PATTERN =
            Pattern.compile("(?im)^(?:Name|नाम)[ \\t]*[:/-]?[ \\t]*(.+)$");

    // ── PAN patterns ────────────────────────────────────────────────────
    private static final Pattern PAN_NUMBER_PATTERN =
            Pattern.compile("(?i)\\b([A-Z]{5}[ \\t-]?\\d{4}[ \\t-]?[A-Z])\\b");
    private static final Pattern PAN_MARKER_PATTERN = Pattern.compile(
            "(?i)\\b(?:permanent account number|income tax department|pan card)\\b|आयकर विभाग|स्थायी लेखा संख्या|पैन कार्ड");
    private static final Pattern PAN_NAME_LABEL_PATTERN = Pattern.compile(
            "(?i)^[ \\t]*(?:Name(?: of Assessee)?|नाम)[ \\t]*[:：/-]?[ \\t]*(.*)$");
    private static final Pattern PAN_FATHER_LABEL_PATTERN = Pattern.compile(
            "(?i)^[ \\t]*(?:Father'?s?[ \\t]+Name|पिता का नाम)[ \\t]*[:：/-]?[ \\t]*(.*)$");
    private static final Pattern PAN_DOB_LABEL_PATTERN = Pattern.compile(
            "(?i)^[ \\t]*(?:Date[ \\t]+of[ \\t]+Birth|DOB|जन्म तिथि)[ \\t]*[:：/-]?[ \\t]*(.*)$");
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
            log.warn("Cloudinary OCR produced no text for {} ({})", documentType, side);
            return new ExtractionResult(false, Map.of(), null, 0.0,
                    "No readable OCR text was returned. Upload a clear document image.");
        }

        log.info("OCR extraction starting for documentType={}, side={}, textLength={}, confidence={}",
                documentType, side, ocrText.length(), confidence);

        return switch (documentType) {
            case AADHAAR -> parseAadhaar(ocrText, confidence, side);
            case PAN -> parsePan(ocrText, confidence);
            case BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE -> parseBarCouncil(ocrText, confidence);
            case DRIVING_LICENSE -> parseDrivingLicense(ocrText, confidence);
        };
    }

    /**
     * Merges extraction results from multiple images (e.g. Aadhaar or Bar ID front + back).
     */
    public ExtractionResult mergeResults(ExtractionResult primary, ExtractionResult secondary) {
        if (!primary.success() || !secondary.success()) {
            return new ExtractionResult(false, Map.of(), null, 0.0,
                    !primary.success() ? primary.error() : secondary.error());
        }
        Map<String, String> merged = new HashMap<>(primary.extractedFields());
        // Add fields from secondary that are missing in primary
        secondary.extractedFields().forEach(merged::putIfAbsent);

        return new ExtractionResult(
                primary.success() && secondary.success(),
                merged,
                primary.maskedDocumentNumber() != null
                        ? primary.maskedDocumentNumber()
                        : secondary.maskedDocumentNumber(),
                Math.max(primary.confidence(), secondary.confidence()),
                null,
                primary.aadhaarNumber() != null ? primary.aadhaarNumber() : secondary.aadhaarNumber(),
                primary.panNumber() != null ? primary.panNumber() : secondary.panNumber()
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

    private ExtractionResult parseAadhaar(String text, double confidence, String side) {
        Map<String, String> fields = new HashMap<>();
        String maskedNumber = null;
        String fullNumber = null;
        boolean isBack = "back".equalsIgnoreCase(side);
        boolean hasAadhaarMarker = AADHAAR_MARKER_PATTERN.matcher(text).find();
        Matcher aadhaarMatcher = AADHAAR_NUMBER_PATTERN.matcher(text);
        boolean invalidNumberFound = false;
        while (aadhaarMatcher.find()) {
            String rawNumber = aadhaarMatcher.group(1).replaceAll("[\\s-]", "");
            if (isValidAadhaarChecksum(rawNumber)) {
                fullNumber = rawNumber;
                maskedNumber = maskAadhaar(rawNumber);
                break;
            }
            invalidNumberFound = true;
        }
        if (!hasAadhaarMarker && maskedNumber == null) {
            return new ExtractionResult(false, Map.of(), null, confidence,
                    "The image does not contain recognizable Aadhaar identifiers.");
        }
        if (isBack) {
            String address = extractAadhaarAddress(text);
            if (address != null) {
                fields.put("address", address);
                Matcher pinMatcher = PINCODE_PATTERN.matcher(address);
                if (pinMatcher.find()) fields.put("pincode", pinMatcher.group(1));
            }
            boolean success = fields.containsKey("address") && fields.containsKey("pincode");
            return new ExtractionResult(success, fields, maskedNumber, confidence,
                    success ? null : "Could not extract address and PIN code from the Aadhaar back Address/पता field.",
                    success ? fullNumber : null);
        }
        if (!hasAadhaarMarker) {
            return new ExtractionResult(false, Map.of(), null, confidence,
                    "The front image does not contain recognizable Aadhaar identifiers.");
        }
        Matcher dobMatcher = AADHAAR_DOB_PATTERN.matcher(text);
        Matcher yobMatcher = AADHAAR_YOB_PATTERN.matcher(text);
        if (dobMatcher.find()) fields.put("dob", dobMatcher.group(1));
        else if (yobMatcher.find()) fields.put("yearOfBirth", yobMatcher.group(1));
        Matcher genderMatcher = GENDER_PATTERN.matcher(text);
        if (genderMatcher.find()) fields.put("gender", genderMatcher.group(1).toUpperCase());
        String name = extractAadhaarName(text);
        if (name != null) fields.put("name", name);
        boolean success = maskedNumber != null && fields.containsKey("name")
                && (fields.containsKey("dob") || fields.containsKey("yearOfBirth"))
                && fields.containsKey("gender");
        String error = success ? null : maskedNumber == null
                ? (invalidNumberFound ? "Aadhaar number failed checksum validation." : "Could not extract Aadhaar number.")
                : !fields.containsKey("name") ? "Could not extract Aadhaar front name."
                : !fields.containsKey("dob") && !fields.containsKey("yearOfBirth")
                    ? "Could not extract labeled Aadhaar DOB or year of birth."
                    : "Could not extract Aadhaar front gender.";

        log.info("Aadhaar extraction result: success={}, fieldsFound={}", success, fields.size());

        return new ExtractionResult(success, fields, maskedNumber, confidence, error,
                success ? fullNumber : null);
    }

    private String extractAadhaarName(String text) {
        Matcher labeled = AADHAAR_NAME_LABEL_PATTERN.matcher(text);
        if (labeled.find()) {
            String candidate = normalizeName(labeled.group(1));
            if (candidate != null && !candidate.isBlank()) return candidate;
        }
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (!AADHAAR_DOB_PATTERN.matcher(lines[i]).find() && !AADHAAR_YOB_PATTERN.matcher(lines[i]).find()) continue;
            for (int j = i - 1; j >= Math.max(0, i - 3); j--) {
                String candidate = normalizeName(lines[j]);
                if (candidate != null && candidate.matches("[\\p{L}][\\p{L} .'-]{2,60}")
                        && !AADHAAR_MARKER_PATTERN.matcher(candidate).find()
                        && !candidate.matches("(?i).*(?:india|भारतीय|authority|enrolment|issue|print|female|male).*")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String extractAadhaarAddress(String text) {
        String[] lines = text.split("\\R");
        String english = readAddressAfterLabel(lines, AADHAAR_ENGLISH_ADDRESS_LABEL);
        if (english != null && PINCODE_PATTERN.matcher(english).find()) return english;
        String hindi = readAddressAfterLabel(lines, AADHAAR_HINDI_ADDRESS_LABEL);
        if (hindi != null && PINCODE_PATTERN.matcher(hindi).find()) return hindi;
        return english != null ? english : hindi;
    }

    private String readAddressAfterLabel(String[] lines, Pattern labelPattern) {
        for (int i = 0; i < lines.length; i++) {
            Matcher label = labelPattern.matcher(lines[i]);
            if (!label.matches()) continue;
            StringBuilder address = new StringBuilder(label.group(1).trim());
            for (int j = i + 1; j < Math.min(lines.length, i + 9)
                    && !PINCODE_PATTERN.matcher(address).find(); j++) {
                String line = lines[j].trim();
                if (line.isEmpty()) continue;
                if (AADHAAR_ADDRESS_STOP.matcher(line).matches()) break;
                if (!address.isEmpty()) address.append(' ');
                address.append(line);
            }
            String result = address.toString().trim();
            if (!result.isEmpty()) return result;
        }
        return null;
    }

    // ── PAN Parser ──────────────────────────────────────────────────────

    private ExtractionResult parsePan(String text, double confidence) {
        Map<String, String> fields = new HashMap<>();
        if (!PAN_MARKER_PATTERN.matcher(text).find()) {
            return new ExtractionResult(false, Map.of(), null, confidence,
                    "The image does not contain recognizable PAN card identifiers.");
        }
        Matcher panMatcher = PAN_NUMBER_PATTERN.matcher(text);
        if (!panMatcher.find()) {
            return new ExtractionResult(false, Map.of(), null, confidence,
                    "Could not extract a valid PAN number from the card.");
        }
        String rawPan = panMatcher.group(1).replaceAll("[ \\t-]", "").toUpperCase();
        if (rawPan.charAt(3) != 'P') {
            return new ExtractionResult(false, Map.of(), null, confidence,
                    "Only an individual PAN card can verify a lawyer profile.");
        }
        fields.put("panType", "Individual");
        String[] lines = text.split("\\R");
        String name = readPanLabeledValue(lines, PAN_NAME_LABEL_PATTERN);
        String fatherName = readPanLabeledValue(lines, PAN_FATHER_LABEL_PATTERN);
        String dateText = readPanLabeledValue(lines, PAN_DOB_LABEL_PATTERN);
        if (name != null) fields.put("name", normalizeName(name));
        if (fatherName != null) fields.put("fatherName", normalizeName(fatherName));
        if (dateText != null) {
            Matcher dobMatcher = DOB_PATTERN.matcher(dateText);
            if (dobMatcher.find()) fields.put("dob", dobMatcher.group(1));
        }

        boolean success = fields.containsKey("name") && fields.containsKey("fatherName")
                && fields.containsKey("dob");
        String error = success ? null : !fields.containsKey("name")
                ? "Could not extract the PAN cardholder name."
                : !fields.containsKey("fatherName") ? "Could not extract the PAN father's name."
                : "Could not extract the labeled PAN date of birth.";

        log.info("PAN extraction result: success={}, fieldsFound={}", success, fields.size());

        return new ExtractionResult(success, fields, success ? maskPan(rawPan) : null, confidence,
                error, null, success ? rawPan : null);
    }

    private String readPanLabeledValue(String[] lines, Pattern labelPattern) {
        for (int i = 0; i < lines.length; i++) {
            Matcher label = labelPattern.matcher(lines[i]);
            if (!label.matches()) continue;
            String value = stripRepeatedPanLabel(label.group(1), labelPattern);
            if (isPanFieldValue(value)) return value;
            for (int j = i + 1; j < lines.length && j <= i + 3; j++) {
                value = stripRepeatedPanLabel(lines[j], labelPattern);
                if (value.isEmpty()) continue;
                if (isPanLabel(value)) break;
                if (isPanFieldValue(value)) return value;
            }
        }
        return null;
    }

    private String stripRepeatedPanLabel(String raw, Pattern labelPattern) {
        String value = raw.trim().replaceFirst("^[\\s:/：-]+", "");
        for (int count = 0; count < 3; count++) {
            Matcher repeated = labelPattern.matcher(value);
            if (!repeated.matches()) break;
            String remainder = repeated.group(1).trim().replaceFirst("^[\\s:/：-]+", "");
            if (remainder.equals(value)) break;
            value = remainder;
        }
        return value;
    }

    private boolean isPanLabel(String value) {
        return PAN_NAME_LABEL_PATTERN.matcher(value).matches()
                || PAN_FATHER_LABEL_PATTERN.matcher(value).matches()
                || PAN_DOB_LABEL_PATTERN.matcher(value).matches();
    }

    private boolean isPanFieldValue(String value) {
        return !value.isBlank() && !isPanLabel(value)
                && !PAN_MARKER_PATTERN.matcher(value).find()
                && !PAN_NUMBER_PATTERN.matcher(value).find();
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
            // Cloudinary's adv_ocr response puts the complete OCR text in the
            // first text annotation's description, not always in fullTextAnnotation.
            List<Map<String, Object>> annotations =
                    (List<Map<String, Object>>) firstPage.get("textAnnotations");
            if (annotations != null && !annotations.isEmpty()) {
                Object description = annotations.get(0).get("description");
                if (description instanceof String value && !value.isBlank()) return value;
            }
            Map<String, Object> fullTextAnnotation =
                    (Map<String, Object>) firstPage.get("fullTextAnnotation");
            return fullTextAnnotation != null ? (String) fullTextAnnotation.get("text") : null;

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
            String error,
            String aadhaarNumber,
            String panNumber
    ) {
        public ExtractionResult(boolean success, Map<String, String> extractedFields,
                                String maskedDocumentNumber, double confidence, String error) {
            this(success, extractedFields, maskedDocumentNumber, confidence, error, null, null);
        }

        public ExtractionResult(boolean success, Map<String, String> extractedFields,
                                String maskedDocumentNumber, double confidence, String error,
                                String aadhaarNumber) {
            this(success, extractedFields, maskedDocumentNumber, confidence, error, aadhaarNumber, null);
        }
    }
}
