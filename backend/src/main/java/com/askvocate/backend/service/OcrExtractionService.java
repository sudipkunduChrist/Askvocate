package com.askvocate.backend.service;

import com.askvocate.backend.exception.OcrExtractionException;
import com.askvocate.backend.model.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses local Tesseract OCR responses into structured identity fields.
 * 
 * <p>Supports documents:
 * <ul>
 *   <li>Aadhaar Card (front + back)</li>
 *   <li>PAN Card</li>
 *   <li>Bar Council ID Card / Enrollment Certificate / Certificate of Practice (COP)</li>
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
    /** Masked Aadhaar printed/PDF form: "XXXX XXXX 1234" (also * and - separators). */
    private static final Pattern AADHAAR_MASKED_PATTERN =
            Pattern.compile("(?i)(?:[x*•●]{4}[\\s-]*){2}(\\d{4})\\b");
    private static final Pattern AADHAAR_MARKER_PATTERN = Pattern.compile(
            "(?i)\\b(?:aadha{1,2}r|uidai|unique identification authority|government of india)\\b|आधार|भारत सरकार");
    private static final Pattern AADHAAR_DOB_PATTERN = Pattern.compile(
            "(?im)\\b(?:DOB|Date\\s*of\\s*Birth)\\s*[:/-]?\\s*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})\\b");
    private static final Pattern AADHAAR_YOB_PATTERN = Pattern.compile(
            "(?im)\\b(?:YOB|Year\\s*of\\s*Birth)\\s*[:/-]?\\s*((?:19|20)\\d{2})\\b");
    private static final Pattern PINCODE_PATTERN = Pattern.compile("\\b([1-9]\\d{5})\\b");
    private static final Pattern AADHAAR_ENGLISH_ADDRESS_LABEL =
            Pattern.compile("(?i)^[ \\t]*Address[ \\t]*[:：/-]?[ \\t]*(.*)$");
    private static final Pattern AADHAAR_ADDRESS_STOP = Pattern.compile(
            "(?i)^[ \\t]*(?:Address|पता|VID|DOB|YOB|Name|Gender|UIDAI|Aadhaar|Unique Identification Authority|www\\.|help@|1947|QR\\s*code|भारत सरकार|Government of India).*|^[2-9]\\d{3}[ \\t-]?\\d{4}[ \\t-]?\\d{4}$");
    private static final Pattern AADHAAR_ADDRESS_INLINE_FOOTER = Pattern.compile(
            "(?i)(?:\\b(?:VID|UIDAI|Aadhaar|www\\.|help@|1947)\\b|आधार|भारत सरकार|\\b[2-9]\\d{3}[ \\t-]?\\d{4}[ \\t-]?\\d{4}\\b)");
    private static final Pattern AADHAAR_ADDRESS_BROKEN_SECTOR = Pattern.compile(
            "(?i)\\bSECTOR\\s*[-:]?\\s*(?:[A-Za-z]+\\s+){1,4}\\d{1,3}\\b");
    private static final Pattern AADHAAR_NAME_EXCLUSION = Pattern.compile(
            "(?i)(?:\\b(?:name|address|dob|yob|gender|male|female|transgender|india|government|authority|uidai|aadhaar|enrolment|issue|print|year|birth|date|father|mother|s/o|d/o|c/o|w/o|qr|photo|signature|valid|vid)\\b|नाम|पता|जन्म|भारत|सरकार|आधार|पुरुष|महिला)");
    private static final Pattern DOB_PATTERN =
            Pattern.compile("\\b(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4})\\b");
    private static final Pattern GENDER_PATTERN =
            Pattern.compile("\\b(MALE|FEMALE|TRANSGENDER)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_AFTER_LABEL_PATTERN =
            Pattern.compile("(?:Name|नाम)\\s*[:/]?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AADHAAR_NAME_LABEL_PATTERN =
            Pattern.compile("(?i)^[ \\t]*Name[ \\t]*[:/-]?[ \\t]*(.*)$");

    // ── PAN patterns ────────────────────────────────────────────────────
    private static final Pattern PAN_NUMBER_PATTERN =
            Pattern.compile("(?i)\\b([A-Z]{5}[ \\t-]?\\d{4}[ \\t-]?[A-Z])\\b");
    /** Valid non-individual PAN holder-type characters: company, HUF, firm, AOP, trust, BOI, local authority, AJP, government. */
    private static final String OTHER_PAN_HOLDER_TYPES = "CHFATBLJG";
    private static final Pattern PAN_MARKER_PATTERN = Pattern.compile(
            "(?i)\\b(?:permanent account number|income tax department|pan card)\\b|आयकर विभाग|स्थायी लेखा संख्या|पैन कार्ड");
    private static final Pattern PAN_NAME_LABEL_PATTERN = Pattern.compile(
            "(?i)^[ \\t]*(?:Name(?: of Assessee)?|नाम)[ \\t]*[:：/-]?[ \\t]*(.*)$");
    private static final Pattern PAN_FATHER_LABEL_PATTERN = Pattern.compile(
            "(?i)^[ \\t]*(?:Father'?s?[ \\t]+Name|पिता का नाम)[ \\t]*[:：/-]?[ \\t]*(.*)$");
    private static final Pattern PAN_DOB_LABEL_PATTERN = Pattern.compile(
            "(?i)^[ \\t]*(?:D[.\\s]?[O0][.\\s]?B\\.?|Date[ \\t]*of[ \\t]*B[i1l]rth|Birth[ \\t]*Date|जन्म[ \\t]*तिथि)[ \\t]*[:：/-]?[ \\t]*(.*)$");
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

    // ── Degree / AIBE patterns ──────────────────────────────────────────
    private static final Pattern CERTIFICATE_YEAR_PATTERN = Pattern.compile("\\b(?:19|20)\\d{2}\\b");
    private static final Pattern CERTIFICATE_PASSING_YEAR_PATTERN = Pattern.compile(
            "(?i)(?:year\\s+of\\s+passing|passing\\s+year|month\\s*(?:&|and)\\s*year\\s+of\\s+passing|academic\\s+session)"
                    + "\\s*[:/-]?\\s*((?:19|20)\\d{2}(?:\\s*[-/]\\s*(?:(?:19|20)?\\d{2}))?)");
    private static final Pattern CERTIFICATE_ISSUE_DATE_PATTERN = Pattern.compile(
            "(?i)\\b(?:date|dated|issued\\s+on)\\s*[:/-]?\\s*((?:[0-3]?\\d[./-][01]?\\d[./-](?:19|20)\\d{2})"
                    + "|(?:(?:19|20)\\d{2}[./-][01]?\\d[./-][0-3]?\\d))\\b");
    private static final Pattern CERTIFICATE_CLASS_PATTERN = Pattern.compile(
            "(?i)\\bclass\\s*(?:obtained\\s*)?[:/-]?\\s*([A-Z][A-Z0-9]*(?:\\s*\\([^\\r\\n)]{1,40}\\))?)");
    private static final Pattern CERTIFICATE_GRADE_PATTERN = Pattern.compile(
            "(?i)\\b([A-F])\\s*\\(\\s*(VERY\\s+GOOD|GOOD|AVERAGE|PASS)\\s*\\)");
    private static final Pattern CERTIFICATE_ROMAN_CLASS_PATTERN = Pattern.compile(
            "(?i)\\b(I{1,3}|FIRST|SECOND|THIRD)\\s+CLASS\\b");
    private static final Pattern CERTIFICATE_DURATION_PATTERN = Pattern.compile(
            "(?i)\\b((?:three|five|3|5)\\s*[- ]?\\s*year(?:s)?)\\b");
    private static final Pattern CERTIFICATE_INSTITUTION_PATTERN =
            Pattern.compile("(?i)(?:University|College|Institute|Board|Bar\\s+Council)[^\\n]*");
    private static final Pattern AIBE_NUMBER_PATTERN =
            Pattern.compile("(?i)(?:AIBE|All[ \\t]+India[ \\t]+Bar[ \\t]+Examination)[ \\t]*"
                    + "(?:Roll|Registration|Reg|Enrollment|Enrolment)?[ \\t]*(?:No\\.?|Number)?[ \\t]*"
                    + "[:/.-]?[ \\t]*([A-Z0-9][A-Z0-9/\\-]*\\d[A-Z0-9/\\-]*)");
    private static final Pattern DEGREE_NUMBER_PATTERN =
            Pattern.compile("(?i)(?:Degree|Registration|Reg|Roll|Certificate)[ \\t]*"
                    + "(?:No\\.?|Number)[ \\t]*[:/.-]?[ \\t]*"
                    + "([A-Z0-9][A-Z0-9/\\-]*\\d[A-Z0-9/\\-]*)");
    private static final Pattern CERTIFICATE_NAME_LABEL_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:Name\\s+of\\s+(?:the\\s+)?(?:Student|Candidate|Holder)|"
                    + "Student'?s?\\s+Name|Candidate'?s?\\s+Name|Name)\\s*[:/-]?\\s*"
                    + "([A-Za-z][A-Za-z .'-]{2,80})\\s*$");
    private static final Pattern CERTIFICATE_INSTITUTION_LINE_PATTERN = Pattern.compile(
            "(?im)^\\s*([A-Z][A-Za-z&.,'()\\- ]{4,120}?\\s+"
                    + "(?:University|Vishwavidyalaya|College|Institute|Vidyalaya|Board))\\s*$");
    private static final Pattern CERTIFICATE_DEGREE_TYPE_PATTERN = Pattern.compile(
            "(?i)\\b((?:B\\.?\\s*A\\.?|B\\.?\\s*B\\.?\\s*A\\.?|B\\.?\\s*Com\\.?|B\\.?\\s*Sc\\.?)?"
                    + "\\s*LL\\.?\\s*B\\.?|Bachelor\\s+of\\s+(?:Arts\\s+and\\s+)?(?:Bachelor\\s+of\\s+)?Laws|"
                    + "LL\\.?\\s*M\\.?|Master\\s+of\\s+Laws|"
                    + "M\\.?\\s*B\\.?\\s*L\\.?|Master\\s+of\\s+Business\\s+Laws|Diploma(?:\\s+in\\s+Law)?)\\b");
    private static final Pattern CERTIFICATE_KIND_PATTERN =
            Pattern.compile("(?i)\\b(FINAL|PROVISIONAL)\\b");
    private static final Pattern CERTIFIES_THAT_LINE =
            Pattern.compile("(?i)\\bcertif(?:y|ies|ied)\\s+that\\b");
    private static final Pattern COLLEGE_NAME_LINE_PATTERN =
            Pattern.compile("(?im)^\\s*(?:College(?:\\s+Name)?|Name\\s+of\\s+(?:the\\s+)?College)"
                    + "(?:\\s+with\\s+code\\s+No\\.?)?\\s*[:/-]?\\s*(.+)$");
    private static final Pattern COLLEGE_FROM_PATTERN =
            Pattern.compile("(?i)\\bfrom\\s*[.:/-]?\\s*([A-Z][A-Za-z&.,'()\\- ]{3,100}?"
                    + "\\b(?:Law\\s+)?College(?:,\\s*[A-Za-z .'-]{2,50})?)\\b");
    private static final Pattern COLLEGE_LINE_PATTERN =
            Pattern.compile("(?im)^\\s*([A-Z][A-Za-z&.,'()\\- ]{3,120}?\\s+College"
                    + "(?:,\\s*[A-Za-z .'-]{2,50})?)\\s*$");
    private static final Pattern EXAM_REGISTRATION_NUMBER_PATTERN =
            Pattern.compile("(?i)\\b(?:Examination\\s*)?(?:Registration|Reg|Enrollment|Enrolment|Roll|Seat|PRN)"
                    + "\\s*(?:No\\.?|Number|#)?\\s*[:/#.-]?\\s*([A-Z0-9][A-Z0-9/.-]{4,24})\\b");
    private static final Pattern CERTIFICATE_SERIAL_NUMBER_PATTERN =
            Pattern.compile("(?i)\\b(?:S\\s*[L1I]\\s*\\.?\\s*No\\.?|Serial\\s*(?:No\\.?|Number|#)|"
                    + "Certificate\\s*(?:No\\.?|Number|#)|Degree\\s*(?:No\\.?|Number|#))"
                    + "\\s*[:/#.-]?\\s*([A-Z0-9][A-Z0-9/.-]{2,24})");
    private static final Pattern PDC_NUMBER_PATTERN =
            Pattern.compile("(?i)\\bPDC\\s*(?:No\\.?|Number|#)?\\s*[:/#.-]?\\s*"
                    + "([A-Z0-9](?:[A-Z0-9/.-]{0,5}[ \\t]+)?\\d{3,})");
    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("\\b(\\d{9,12})\\b");
    private static final Set<String> NAME_STOP_WORDS = Set.of(
            "university", "college", "institute", "school", "board", "examination", "registration",
            "degree", "bachelor", "master", "diploma", "certificate", "class", "date", "bar",
            "council", "name", "address", "father", "mother", "signature", "registrar", "chancellor",
            "seal", "state", "law", "karnataka", "hubballi", "photograph", "certifies", "that",
            "given", "under", "vice", "very", "good", "arts", "science", "commerce", "marks",
            "marksheet", "semester", "transcript", "provisional", "final", "awarded", "admitted",
            "this", "recognition", "fulfilment", "requirements", "said", "number", "roll", "exam",
            "laws", "vice-chancellor", "vicechancellor", "year", "month", "session", "page",
            "serial", "total", "grade", "cgpa", "percentage", "course", "branch",
            "config", "tha", "cot", "tew", "con", "com", "unive", "oop", "cooh", "esd", "oenod",
            "vidyapeeth", "vidyapecth", "vidyapoolh", "bharati", "bharali", "bhanati", "ghanati",
            "deemed", "band", "bead", "wet", "dera");

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
     * @param rawOcrData   text and confidence returned by the local vision service
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
        List<String> texts = extractTextCandidates(rawOcrData);
        double confidence = extractConfidenceFromOcrResponse(rawOcrData);

        if (texts.isEmpty()) {
            log.warn("Local OCR produced no text for {} ({})", documentType, side);
            return new ExtractionResult(false, Map.of(), null, 0.0,
                    "No readable OCR text was returned. Upload a clear document image.");
        }

        log.info("OCR extraction starting for documentType={}, side={}, candidates={}, confidence={}",
                documentType, side, texts.size(), confidence);

        ExtractionResult best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        List<ExtractionResult> parsed = new ArrayList<>();
        for (String text : texts) {
            if (log.isDebugEnabled()) {
                log.debug("OCR candidate for {} {}: {}", documentType, side, text);
            }
            ExtractionResult candidate = parseText(text, documentType, side, confidence);
            parsed.add(candidate);
            double score = extractionScore(candidate, documentType);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        // Certificates are often split across Tesseract passes: one pass reads the
        // holder name, another the university/degree line. Merge missing fields from
        // the other successful reads instead of discarding them.
        if (best != null
                && (documentType == DocumentType.DEGREE_CERTIFICATE
                || documentType == DocumentType.AIBE_CERTIFICATE)) {
            Map<String, String> merged = new LinkedHashMap<>(best.extractedFields());
            for (ExtractionResult candidate : parsed) {
                if (candidate == null || candidate.extractedFields().isEmpty()) continue;
                candidate.extractedFields().forEach((key, value) -> {
                    if (value == null || value.isBlank()) return;
                    String current = merged.get(key);
                    if (current == null || current.isBlank()) {
                        merged.put(key, value);
                    } else if (("institution".equals(key) || "college".equals(key))
                            && institutionQuality(value) > institutionQuality(current)) {
                        merged.put(key, value);
                    } else if ("degreeType".equals(key) && value.length() > current.length()) {
                        merged.put(key, value);
                    } else if ("name".equals(key) && nameQuality(value) > nameQuality(current)) {
                        merged.put(key, value);
                    }
                });
            }
            Object nameCandidate = rawOcrData instanceof Map<?, ?> map ? map.get("nameCandidate") : null;
            if (nameCandidate instanceof String value && !value.isBlank()) {
                String clean = mixedCaseName(value);
                if (clean == null) clean = cleanPersonName(value);
                if (clean != null && nameQuality(clean) > nameQuality(merged.get("name"))) {
                    merged.put("name", clean);
                }
            }
            boolean success = merged.containsKey("name");
            best = new ExtractionResult(success, merged, best.maskedDocumentNumber(),
                    best.confidence(), success ? null : best.error(),
                    best.aadhaarNumber(), best.panNumber());
        }
        if (best != null) {
            log.info("OCR extraction chosen for documentType={}, side={}, success={}, fields={}, error={}",
                    documentType, side, best.success(), best.extractedFields().keySet(), best.error());
        }
        return best;
    }

    /**
     * Picks the candidate name that best matches the verified Aadhaar/PAN name.
     * Low-resolution certificates often put the correct name in only one of the
     * Tesseract passes, while other passes produce plausible-looking noise.
     */
    public String bestNameFor(Object rawOcrData, String expectedName) {
        if (expectedName == null || expectedName.isBlank()) return null;
        String expectedNormalized = normalizeName(expectedName);
        String expectedFirst = expectedNormalized.split("\\s+")[0];
        String best = null;
        double bestScore = 0;
        for (String text : extractTextCandidates(rawOcrData)) {
            String[] tokens = text.split("\\s+");
            for (int start = 0; start < tokens.length; start++) {
                StringBuilder window = new StringBuilder();
                for (int length = 1; length <= 4 && start + length <= tokens.length; length++) {
                    String token = tokens[start + length - 1]
                            .replaceAll("^[^A-Za-z]+", "")
                            .replaceAll("[^A-Za-z.'-]+$", "");
                    if (token.isEmpty() || token.length() > 20) break;
                    if (window.length() > 0) window.append(' ');
                    window.append(token);
                    String candidate = window.toString();
                    if (candidate.length() < 2 || candidate.length() > 60) continue;
                    if (!looksLikeNameWindow(candidate)) continue;
                    int exact = exactTokenMatches(candidate, expectedName);
                    if (exact == 0) continue;
                    double score = Math.max(editSimilarity(candidate, expectedName),
                            (double) exact / Math.max(candidate.split("\\s+").length,
                                    expectedNormalized.split("\\s+").length));
                    String candidateFirst = normalizeName(candidate).split("\\s+")[0];
                    if (candidateFirst.equals(expectedFirst)
                            || candidateFirst.startsWith(expectedFirst)
                            || expectedFirst.startsWith(candidateFirst)) {
                        score += 0.25;
                    }
                    if (score > bestScore + 1e-9
                            || (Math.abs(score - bestScore) <= 1e-9 && best != null
                            && candidate.split("\\s+").length > best.split("\\s+").length)) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return bestScore >= 0.5 ? best : null;
    }

    private static int exactTokenMatches(String first, String second) {
        Set<String> left = new java.util.HashSet<>(java.util.Arrays.asList(normalizeStatic(first).split(" ")));
        Set<String> right = new java.util.HashSet<>(java.util.Arrays.asList(normalizeStatic(second).split(" ")));
        int common = 0;
        for (String token : left) if (token.length() >= 2 && right.contains(token)) common++;
        return common;
    }

    private double editSimilarity(String first, String second) {
        String a = normalizeName(first);
        String b = normalizeName(second);
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0;
        if (a.equals(b)) return 1.0;
        return 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
    }

    private static String normalizeStatic(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeNameWindow(String candidate) {
        boolean hasLongToken = false;
        for (String token : candidate.split("\\s+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (NAME_STOP_WORDS.contains(lower)) return false;
            if (token.length() >= 3) hasLongToken = true;
        }
        return hasLongToken;
    }

    private double nameSimilarity(String first, String second) {
        if (first == null || second == null) return 0;
        String a = normalizeName(first);
        String b = normalizeName(second);
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0;
        if (a.equals(b) || a.contains(b) || b.contains(a)) return 1.0;
        int longest = Math.max(a.length(), b.length());
        double edit = 1.0 - (double) levenshtein(a, b) / longest;
        Set<String> left = new java.util.HashSet<>(java.util.Arrays.asList(a.split(" ")));
        Set<String> right = new java.util.HashSet<>(java.util.Arrays.asList(b.split(" ")));
        Set<String> used = new java.util.HashSet<>();
        int common = 0;
        for (String token : left) {
            for (String candidate : right) {
                if (used.contains(candidate)) continue;
                if (token.equals(candidate)
                        || (token.length() == 1 && candidate.startsWith(token))
                        || (candidate.length() == 1 && token.startsWith(candidate))) {
                    used.add(candidate);
                    common++;
                    break;
                }
            }
        }
        double tokens = (double) common / Math.min(left.size(), right.size());
        return Math.max(edit, tokens);
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** The worker returns several Tesseract reads; try all of them before choosing fields. */    public List<String> extractTextCandidates(Object rawOcrData) {
        List<String> texts = new ArrayList<>();
        if (rawOcrData instanceof Map<?, ?> map && map.get("candidates") instanceof List<?> candidates) {
            for (Object item : candidates) {
                if (item instanceof String text && !text.isBlank() && !texts.contains(text)) {
                    texts.add(text);
                }
            }
        }
        String primary = extractTextFromOcrResponse(rawOcrData);
        if (primary != null && !primary.isBlank() && !texts.contains(primary)) {
            texts.add(0, primary);
        }
        return texts;
    }

    private ExtractionResult parseText(String ocrText, DocumentType documentType, String side, double confidence) {
        return switch (documentType) {
            case AADHAAR -> parseAadhaar(ocrText, confidence, side);
            case PAN -> parsePan(ocrText, confidence);
            case BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE -> parseBarCouncil(ocrText, confidence);
            case DEGREE_CERTIFICATE, AIBE_CERTIFICATE -> parseCertificate(ocrText, documentType, confidence);
        };
    }

    private double extractionScore(ExtractionResult result, DocumentType documentType) {
        if (result == null) return Double.NEGATIVE_INFINITY;
        double score = result.success() ? 100 : 0;
        score += result.extractedFields().size();
        if (result.maskedDocumentNumber() != null) score += 10;
        if (documentType == DocumentType.PAN) {
            if (result.extractedFields().containsKey("name")) score += 5;
            if (result.extractedFields().containsKey("fatherName")) score += 5;
            if (result.extractedFields().containsKey("dob")) score += 10;
        } else if (documentType == DocumentType.AADHAAR) {
            if (result.extractedFields().containsKey("name")) score += 5;
            if (result.extractedFields().containsKey("dob")) score += 5;
            if (result.extractedFields().containsKey("gender")) score += 5;
            if (result.extractedFields().containsKey("address")) score += 5;
        } else if (documentType == DocumentType.DEGREE_CERTIFICATE
                || documentType == DocumentType.AIBE_CERTIFICATE) {
            if (result.extractedFields().containsKey("name")) score += 5;
            if (result.extractedFields().containsKey("year")) score += 3;
            if (result.extractedFields().containsKey("institution")) score += 3;
            if (result.extractedFields().containsKey("degreeType")) score += 5;
            if (result.extractedFields().containsKey("certificateType")) score += 3;
            if (result.extractedFields().containsKey("degreeNumber")
                    || result.extractedFields().containsKey("aibeNumber")) score += 3;
            if (result.extractedFields().containsKey("institution")) score += 5;
            if (result.extractedFields().containsKey("college")) score += 4;
            if (result.extractedFields().containsKey("registrationNumber")) score += 6;
            if (result.extractedFields().containsKey("certificateNumber")) score += 2;
            if (result.extractedFields().containsKey("issueDate")) score += 2;
            if (result.extractedFields().containsKey("classObtained")) score += 2;
        }
        return score;
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

    /** Aadhaar's front supplies identity fields; its back supplies the address. */
    public ExtractionResult mergeAadhaarResults(ExtractionResult front, ExtractionResult back) {
        Map<String, String> fields = new HashMap<>(front.extractedFields());
        back.extractedFields().forEach(fields::putIfAbsent);
        boolean success = front.success() && back.success();
        return new ExtractionResult(success, fields, front.maskedDocumentNumber(),
                Math.max(front.confidence(), back.confidence()),
                !front.success() ? front.error() : !back.success() ? back.error() : null,
                front.aadhaarNumber());
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

    // ── Degree / AIBE parser ────────────────────────────────────────────

    private ExtractionResult parseCertificate(String text, DocumentType documentType, double confidence) {
        Map<String, String> fields = new HashMap<>();
        String name = extractCertificateName(text);
        if (name != null) fields.put("name", name);

        String institution = extractInstitution(text, fields);
        if (institution != null) {
            fields.put("institution", institution);
        }

        Matcher passingYear = CERTIFICATE_PASSING_YEAR_PATTERN.matcher(text);
        if (passingYear.find()) {
            String value = normalizeWhitespace(passingYear.group(1));
            fields.put("passingYear", value);
            Matcher yearToken = CERTIFICATE_YEAR_PATTERN.matcher(value);
            if (yearToken.find()) fields.put("year", yearToken.group());
        }
        if (!fields.containsKey("year")) {
            String awardYear = extractAwardYear(text);
            if (awardYear != null) fields.put("year", awardYear);
        }
        Matcher issueDate = CERTIFICATE_ISSUE_DATE_PATTERN.matcher(text);
        if (issueDate.find()) fields.put("issueDate", issueDate.group(1));

        Matcher degreeType = CERTIFICATE_DEGREE_TYPE_PATTERN.matcher(text);
        if (degreeType.find()) {
            fields.put("degreeType", normalizeWhitespace(degreeType.group(1)).toUpperCase(Locale.ROOT));
        }
        Matcher kind = CERTIFICATE_KIND_PATTERN.matcher(text);
        if (kind.find()) fields.put("certificateType", kind.group(1).toUpperCase(Locale.ROOT));

        Matcher registration = EXAM_REGISTRATION_NUMBER_PATTERN.matcher(text);
        while (registration.find()) {
            String value = cleanCertificateNumber(registration.group(1));
            if (value != null) {
                fields.put("registrationNumber", value);
                break;
            }
        }
        if (!fields.containsKey("registrationNumber")
                && text.toLowerCase(Locale.ROOT).contains("registration")) {
            Matcher longNumber = LONG_NUMBER_PATTERN.matcher(text);
            if (longNumber.find()) fields.put("registrationNumber", longNumber.group(1));
        }

        Matcher serial = CERTIFICATE_SERIAL_NUMBER_PATTERN.matcher(text);
        if (serial.find()) {
            String value = cleanCertificateNumber(serial.group(1));
            if (value != null) {
                fields.put("serialNumber", value);
                fields.put("certificateNumber", value);
            }
        }
        if (!fields.containsKey("certificateNumber")) {
            Matcher pdc = PDC_NUMBER_PATTERN.matcher(text);
            if (pdc.find()) {
                String value = cleanCertificateNumber(pdc.group(1));
                if (value != null) fields.put("certificateNumber", value);
            }
        }
        Matcher degree = DEGREE_NUMBER_PATTERN.matcher(text);
        if (degree.find()) {
            String value = cleanCertificateNumber(degree.group(1));
            if (value != null) fields.put("degreeNumber", value);
        }

        Matcher grade = CERTIFICATE_GRADE_PATTERN.matcher(text);
        if (grade.find()) {
            fields.put("classObtained", grade.group(1).toUpperCase(Locale.ROOT) + " ("
                    + normalizeWhitespace(grade.group(2)).toUpperCase(Locale.ROOT) + ")");
        } else {
            Matcher classResult = CERTIFICATE_CLASS_PATTERN.matcher(text);
            if (classResult.find()) {
                String value = normalizeWhitespace(classResult.group(1));
                if (value.length() >= 2
                        && !Set.of("OF", "THE", "DEGREE", "OBTAINED", "EN", "ED")
                        .contains(value.toUpperCase(Locale.ROOT))) {
                    fields.put("classObtained", value);
                }
            } else {
                Matcher romanClass = CERTIFICATE_ROMAN_CLASS_PATTERN.matcher(text);
                if (romanClass.find()) fields.put("classObtained", romanClass.group(1).toUpperCase(Locale.ROOT));
            }
        }
        Matcher duration = CERTIFICATE_DURATION_PATTERN.matcher(text);
        if (duration.find()) fields.put("courseDuration", normalizeWhitespace(duration.group(1)));

        if (documentType == DocumentType.AIBE_CERTIFICATE) {
            Matcher aibe = AIBE_NUMBER_PATTERN.matcher(text);
            if (aibe.find()) fields.put("aibeNumber", cleanCertificateNumber(aibe.group(1)));
        }
        boolean success = fields.containsKey("name");
        String error = success ? null : "Could not read the holder's name from the certificate.";
        return new ExtractionResult(success, fields, null, confidence, error);
    }

    /**
     * Certificate names appear as {@code Name: ...}, "Certifies that <name>", or on
     * the line after "Certifies that". The last case is common on university
     * certificates printed over a watermark.
     */
    private String extractCertificateName(String text) {
        Matcher labeled = CERTIFICATE_NAME_LABEL_PATTERN.matcher(text);
        if (labeled.find()) {
            String name = cleanPersonName(labeled.group(1));
            if (name != null) return name;
        }
        String after = nameAfterCertifies(text);
        if (after != null) return after;
        Matcher certified = CERTIFIED_THAT_PATTERN.matcher(text);
        if (certified.find()) {
            String name = mixedCaseName(certified.group(1));
            if (name == null) name = cleanPersonName(certified.group(1));
            if (name != null) return name;
        }
        // Watermark-heavy certificates often lose the "Certifies that" line but keep
        // the mixed-case name embedded in an all-caps watermark line.
        for (String line : text.split("\\R")) {
            if (!line.matches(".*\\b[A-Z]{4,}\\b.*")) continue;
            String name = mixedCaseName(line);
            if (name != null && name.split(" ")[0].length() >= 5) return name;
        }
        String noisyToken = noisyNameToken(text);
        if (noisyToken != null) return noisyToken;
        return null;
    }

    /**
     * Low-resolution scans sometimes produce a single clean name token inside an
     * otherwise noisy line, e.g. "Bead tie Ren ay Ps Ayushi bi HL".
     */
    private String noisyNameToken(String text) {
        for (String line : text.split("\\R")) {
            if (line.matches("(?i).*\\b(?:University|College|Institute|School|Seal|Given|Under|"
                    + "Chancellor|Signature|Testimony|Examination|Registration|Board)\\b.*")) {
                continue;
            }
            long shortLower = 0;
            for (String token : line.split("\\s+")) {
                if (token.matches("[a-z]{1,4}")) shortLower++;
            }
            if (shortLower < 3) continue;
            java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
            Matcher matcher = Pattern.compile("\\b([A-Z][a-z]{4,8})\\b").matcher(line);
            while (matcher.find()) {
                String token = matcher.group(1);
                if (!NAME_STOP_WORDS.contains(token.toLowerCase(Locale.ROOT))) {
                    candidates.add(token);
                }
            }
            if (candidates.size() == 1) return candidates.iterator().next();
        }
        return null;
    }

    private static int institutionQuality(String value) {
        if (value == null) return Integer.MIN_VALUE;
        String text = value.toLowerCase(Locale.ROOT);
        int score = 0;
        if (text.matches(".*\\b(?:university|vishwavidyalaya)\\b.*")) score += 6;
        if (text.matches(".*\\b(?:college|institute)\\b.*")) score += 4;
        if (value.length() <= 120) score += 2;
        if (value.matches("(?i).*\\b(?:examination|registration|testimony|signature|chancellor)\\b.*")) {
            score -= 6;
        }
        if (value.matches("(?i)^(?:given|under|the|university\\s+and\\b|in\\s+testimony|seal\\b|"
                + "signatures?\\b).*")) score -= 12;
        if (value.matches("(?i).*\\b(?:given|under|seal|testimony|signatures?|chancellor)\\b.*")) {
            score -= 8;
        }
        long letters = value.chars().filter(Character::isLetter).count();
        long uppers = value.chars().filter(Character::isUpperCase).count();
        if (letters > 0 && uppers * 10 > letters * 6) score -= 4;
        if (value.matches(".*[!|].*")) score -= 2;
        if (value.contains(",")) score += 1;
        score += Math.min(4, value.length() / 25);
        return score;
    }

    private boolean hasTooManyDigits(String value) {
        long digits = value.chars().filter(Character::isDigit).count();
        long letters = value.chars().filter(Character::isLetter).count();
        return digits > 0 && (letters < 4 || digits * 2 > letters);
    }

    private String nameAfterCertifies(String text) {
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (!CERTIFIES_THAT_LINE.matcher(lines[i]).find()) continue;
            String sameLine = lines[i].replaceFirst(
                    "(?i).*\\bcertif(?:y|ies|ied)\\s+that\\b", "").trim();
            if (looksLikeNameLine(sameLine)) {
                String candidate = mixedCaseName(sameLine);
                if (candidate == null) candidate = cleanPersonName(sameLine);
                if (candidate != null) return candidate;
            }
            for (int j = i + 1; j < Math.min(lines.length, i + 8); j++) {
                if (!looksLikeNameLine(lines[j])) continue;
                String candidate = mixedCaseName(lines[j]);
                if (candidate == null) candidate = cleanPersonName(lines[j]);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private boolean looksLikeNameLine(String line) {
        String value = line == null ? "" : line.trim()
                .replaceAll("^[^A-Za-z]+", "")
                .replaceAll("[^A-Za-z .'-]+$", "");
        if (value.isEmpty() || value.length() > 60) return false;
        if (hasTooManyDigits(value)) return false;
        String[] tokens = value.split("\\s+");
        if (tokens.length == 0 || tokens.length > 4) return false;
        if (!Character.isUpperCase(tokens[0].charAt(0))) return false;
        for (String token : tokens) {
            if (NAME_STOP_WORDS.contains(token.toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    /** Strict parser for explicit "Name:" labels. */
    private String cleanPersonName(String raw) {
        if (raw == null) return null;
        String value = normalizeName(raw);
        if (value == null || value.isBlank()) return null;
        value = value.replaceAll("(?i)^(?:Mr\\.?|Mrs\\.?|Ms\\.?|Shri|Smt\\.?|Dr\\.?)\\s+", "").trim();

        String[] tokens = value.split("\\s+");
        StringBuilder kept = new StringBuilder();
        for (String token : tokens) {
            if (kept.length() > 0 && token.length() >= 4
                    && token.equals(token.toUpperCase(Locale.ROOT))) break;
            if (kept.length() > 0) kept.append(' ');
            kept.append(token);
        }
        value = kept.toString().trim();
        if (value.isEmpty() || value.length() > 80) return null;
        if (!Character.isUpperCase(value.charAt(0))) return null;
        if (!value.matches("[A-Za-z][A-Za-z .'-]{2,79}")) return null;
        if (value.split("\\s+").length > 6) return null;
        if (value.matches("(?i).*\\b(?:University|College|Institute|School|Board|Examination|"
                + "Registration|Degree|Bachelor|Master|Diploma|Certificate|Class|Date|Bar|Council|"
                + "Name|Address|Father|Mother|Signature|Registrar|Chancellor|Seal|State|Law|"
                + "Karnataka|Hubballi|Photograph|Vishwavidyalaya|Vidyapeeth|Vidyapecth|Vidyapoolh|"
                + "Bharati|Bharali|Bhanati|Ghanati|Deemed)\\b.*")) return null;
        if (hasTooManyDigits(value)) return null;
        if (value.equals(value.toUpperCase(Locale.ROOT)) && value.contains(" ")) return null;
        return isPlausibleCertificateName(value) ? value : null;
    }

    /**
     * Pulls a mixed-case person name out of a noisy certificate line. All-caps
     * watermark words (KARNATAKA, NIVERSITY, ...) and boilerplate labels are skipped.
     */
    private String mixedCaseName(String raw) {
        if (raw == null) return null;
        String value = normalizeName(raw);
        if (value == null || value.isBlank()) return null;
        if (value.matches("(?i).*\\b(?:College|School|Institute|Address|Father|Mother|"
                + "Examination|Registration|Config|Certifies|University|Vidyapeeth|Vidyapecth|"
                + "Vidyapoolh|Bharati|Bharali|Bhanati|Ghanati|Deemed)\\b.*")) return null;
        if (hasTooManyDigits(value)) return null;
        if (value.split("\\s+").length > 4) return null;

        List<String> run = new ArrayList<>();
        for (String token : value.split("\\s+")) {
            String word = token.replaceAll("^[^A-Za-z]+", "")
                    .replaceAll("[^A-Za-z.'-]+$", "");
            if (word.isEmpty()) continue;
            if (word.length() == 1) {
                if (!run.isEmpty() && Character.isUpperCase(word.charAt(0)) && run.size() < 4) {
                    run.add(word);
                    continue;
                }
                if (!run.isEmpty()) break;
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            if (lower.startsWith("vice") || lower.startsWith("chancell")
                    || lower.startsWith("registr") || lower.startsWith("controll")
                    || lower.startsWith("cifancel")) {
                if (!run.isEmpty()) break;
                continue;
            }
            boolean mixedCase = !word.equals(word.toUpperCase(Locale.ROOT));
            boolean upperStart = Character.isUpperCase(word.charAt(0));
            long upperCount = word.chars().filter(Character::isUpperCase).count();
            if (mixedCase && upperStart && upperCount <= 2 && word.length() >= 3
                    && !NAME_STOP_WORDS.contains(lower)) {
                run.add(word);
            } else if (!run.isEmpty() && !mixedCase && word.length() >= 2 && word.length() <= 5
                    && !NAME_STOP_WORDS.contains(lower) && run.size() <= 2) {
                // OCR often splits a name into "Ayu" + "shi" across watermark text.
                int last = run.size() - 1;
                run.set(last, run.get(last) + word);
            } else if (!run.isEmpty()) {
                break;
            }
        }
        if (run.isEmpty() || run.get(0).length() < 3) return null;
        String name = String.join(" ", run);
        return name.length() <= 80 && isPlausibleCertificateName(name) ? name : null;
    }

    private static boolean isPlausibleCertificateName(String value) {
        for (String token : value.split("\\s+")) {
            long upper = token.chars().filter(Character::isUpperCase).count();
            if (upper > 2 || token.length() > 20) return false;
            if (NAME_STOP_WORDS.contains(token.toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    private static int nameQuality(String value) {
        if (value == null || value.isBlank()) return Integer.MIN_VALUE;
        String[] tokens = value.split("\\s+");
        int score = value.replace(" ", "").length() * 2 + tokens[0].length() * 2;
        for (String token : tokens) {
            long upper = token.chars().filter(Character::isUpperCase).count();
            if (upper > 1) score -= 3;
            if (token.length() < 3) score -= 2;
        }
        return score;
    }

    private String extractAwardYear(String text) {
        int currentYear = java.time.LocalDate.now().getYear();
        int best = -1;
        for (String line : text.split("\\R")) {
            if (line.matches("(?i).*(?:\\bUGC\\b|\\bAct\\b|established|u/s|section|regulation|"
                    + "founded|since|university\\s+act).*")) {
                continue;
            }
            Matcher matcher = CERTIFICATE_YEAR_PATTERN.matcher(line);
            while (matcher.find()) {
                int year = Integer.parseInt(matcher.group());
                if (year >= 1950 && year <= currentYear + 1) best = Math.max(best, year);
            }
        }
        return best > 0 ? String.valueOf(best) : null;
    }

    private String extractInstitution(String text, Map<String, String> fields) {
        String college = extractCollegeName(text);
        if (college != null) fields.put("college", college);

        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String line : text.split("\\R")) {
            String value = cleanInstitution(line);
            if (value == null || value.length() > 120) continue;
            if (!value.matches("(?i).*\\b(?:University|Vishwavidyalaya)\\b.*")) continue;
            if (value.matches("(?i).*\\b(?:Testimony|Seal|Signatures?|Chancellor|Vice|Conferred|"
                    + "Examination|Academic\\s+Council|Board\\s+of\\s+Management|Given|Under)\\b.*")) continue;
            String lower = value.toLowerCase(Locale.ROOT);
            int occurrences = countOccurrences(lower, "university")
                    + countOccurrences(lower, "vishwavidyalaya");
            if (occurrences > 1) continue;
            int score = 0;
            if (!value.equals(value.toUpperCase(Locale.ROOT))) score += 3;
            if (value.contains(",")) score += 2;
            // "Deemed University, Pune" alone is a fragment; prefer the college line.
            if (value.matches("(?i)^(?:deemed\\s+)?university\\b.*")) score -= 5;
            if (score > bestScore) {
                bestScore = score;
                best = value;
            }
        }
        if (best == null || best.matches("(?i)^(?:deemed\\s+)?university\\b.*")) {
            String collegeName = fields.get("college");
            if (collegeName != null) best = collegeName;
        }
        if (best == null) {
            Matcher fallback = CERTIFICATE_INSTITUTION_PATTERN.matcher(text);
            while (fallback.find()) {
                String value = cleanInstitution(fallback.group());
                if (value != null && !value.equals(value.toUpperCase(Locale.ROOT))
                        && value.split("\\s+").length <= 10) {
                    best = value;
                    break;
                }
            }
        }
        return best != null ? best : fields.get("college");
    }

    private String extractCollegeName(String text) {
        Matcher labeled = COLLEGE_NAME_LINE_PATTERN.matcher(text);
        while (labeled.find()) {
            String value = cleanInstitution(labeled.group(1));
            if (value != null && value.toLowerCase(Locale.ROOT).contains("college")) return value;
        }
        Matcher from = COLLEGE_FROM_PATTERN.matcher(text);
        if (from.find()) {
            String value = cleanInstitution(from.group(1));
            if (value != null) return value;
        }
        Matcher line = COLLEGE_LINE_PATTERN.matcher(text);
        while (line.find()) {
            String value = cleanInstitution(line.group(1));
            if (value != null && value.toLowerCase(Locale.ROOT).contains("college")) return value;
        }
        return null;
    }

    private String cleanCertificateNumber(String raw) {
        if (raw == null) return null;
        String value = raw.replaceAll("\\s+", "").replaceAll("[^A-Za-z0-9/.-]", "")
                .toUpperCase(Locale.ROOT);
        return value.length() >= 4 && value.matches(".*\\d.*") ? value : null;
    }

    private String cleanInstitution(String raw) {
        if (raw == null) return null;
        String value = normalizeWhitespace(raw)
                .replaceAll("^[^A-Za-z]+", "")
                .replaceAll("[^A-Za-z.)]+$", "")
                .replaceAll("[:;|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.matches("(?i)^(?:given|under|the|university\\s+and\\b|in\\s+testimony|seal\\b|"
                + "signatures?\\b).*")) return null;
        if (value.matches("(?i).*\\b(?:given|under|seal|testimony|signatures?|chancellor)\\b.*")) {
            return null;
        }
        return value.length() >= 6 ? value : null;
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    // ── Aadhaar Parser ──────────────────────────────────────────────────

    private ExtractionResult parseAadhaar(String text, double confidence, String side) {
        Map<String, String> fields = new HashMap<>();
        String maskedNumber = null;
        String fullNumber = null;
        boolean isBack = "back".equalsIgnoreCase(side);
        Matcher aadhaarMatcher = AADHAAR_NUMBER_PATTERN.matcher(text);
        String firstNumber = null;
        while (aadhaarMatcher.find()) {
            String rawNumber = aadhaarMatcher.group(1).replaceAll("[\\s-]", "");
            if (firstNumber == null) firstNumber = rawNumber;
            if (isValidAadhaarChecksum(rawNumber)) {
                fullNumber = rawNumber;
                break;
            }
        }
        if (fullNumber == null) fullNumber = firstNumber;
        if (fullNumber != null) {
            maskedNumber = maskAadhaar(fullNumber);
        } else {
            Matcher maskedMatcher = AADHAAR_MASKED_PATTERN.matcher(text);
            if (maskedMatcher.find()) maskedNumber = "XXXX-XXXX-" + maskedMatcher.group(1);
        }
        if (isBack) {
            String address = extractAadhaarAddress(text);
            if (address != null) {
                fields.put("address", address);
                Matcher pinMatcher = PINCODE_PATTERN.matcher(address);
                if (pinMatcher.find()) fields.put("pincode", pinMatcher.group(1));
            }
            boolean success = fields.containsKey("address");
            return new ExtractionResult(success, fields, maskedNumber, confidence,
                    success ? null : "Could not extract the English Aadhaar back Address field.");
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
                && fields.containsKey("dob")
                && fields.containsKey("gender");
        String error = success ? null : maskedNumber == null
                ? "Could not extract the Aadhaar number or its masked last four digits from the front."
                : !fields.containsKey("name") ? "Could not extract Aadhaar front name."
                : !fields.containsKey("dob")
                    ? "Could not extract labeled Aadhaar date of birth from the front."
                    : "Could not extract Aadhaar front gender.";

        log.info("Aadhaar extraction result: success={}, fieldsFound={}", success, fields.size());

        return new ExtractionResult(success, fields, maskedNumber, confidence, error, fullNumber);
    }

    private String extractAadhaarName(String text) {
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            Matcher labeled = AADHAAR_NAME_LABEL_PATTERN.matcher(lines[i]);
            if (!labeled.matches()) continue;
            String candidate = normalizeName(labeled.group(1));
            if (isAadhaarNameCandidate(candidate)) return candidate;
            candidate = bestAadhaarName(lines, i + 1, Math.min(lines.length, i + 3), i);
            if (candidate != null) return candidate;
        }
        int birthLine = -1;
        int genderLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (birthLine < 0 && (AADHAAR_DOB_PATTERN.matcher(lines[i]).find()
                    || AADHAAR_YOB_PATTERN.matcher(lines[i]).find())) birthLine = i;
            if (genderLine < 0 && GENDER_PATTERN.matcher(lines[i]).find()) genderLine = i;
        }
        int anchor = birthLine >= 0 ? birthLine : genderLine;
        if (anchor < 0) return null;
        String candidate = bestAadhaarName(lines, Math.max(0, anchor - 8), anchor, anchor);
        if (candidate != null) return candidate;
        // Some OCR layouts place the printed name after the DOB/gender row.
        return bestAadhaarName(lines, anchor + 1, Math.min(lines.length, anchor + 5), anchor);
    }

    private String bestAadhaarName(String[] lines, int start, int end, int anchor) {
        String best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            String raw = lines[i].trim();
            if (AADHAAR_DOB_PATTERN.matcher(raw).find() || AADHAAR_YOB_PATTERN.matcher(raw).find()) break;
            String candidate = normalizeName(raw);
            if (!isAadhaarNameCandidate(candidate)) continue;
            int words = candidate.split(" ").length;
            double score = candidate.length() + Math.min(words, 4) * 4
                    - Math.abs(anchor - i) * 0.5;
            // OCR of the regional-script line often resembles a short English name.
            // Prefer the complete, clean printed English line when both are present.
            if (!raw.matches("[A-Za-z][A-Za-z .'-]{2,60}")) score -= 12;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean isAadhaarNameCandidate(String candidate) {
        return candidate != null && candidate.matches("[A-Za-z][A-Za-z .'-]{2,60}")
                && !AADHAAR_MARKER_PATTERN.matcher(candidate).find()
                && !AADHAAR_NAME_EXCLUSION.matcher(candidate).find();
    }

    private String extractAadhaarAddress(String text) {
        String[] lines = text.split("\\R");
        return readAddressAfterLabel(lines, AADHAAR_ENGLISH_ADDRESS_LABEL);
    }

    private String readAddressAfterLabel(String[] lines, Pattern labelPattern) {
        for (int i = 0; i < lines.length; i++) {
            Matcher label = labelPattern.matcher(lines[i]);
            if (!label.matches()) continue;
            StringBuilder address = new StringBuilder(cleanAadhaarAddressLine(label.group(1)));
            for (int j = i + 1; j < Math.min(lines.length, i + 7)
                    && !PINCODE_PATTERN.matcher(address).find(); j++) {
                String line = lines[j].trim();
                if (line.isEmpty()) continue;
                if (AADHAAR_ADDRESS_STOP.matcher(line).matches()) break;
                line = cleanAadhaarAddressLine(line);
                if (line.isEmpty()) break;
                if (!address.isEmpty()) address.append(' ');
                address.append(line);
            }
            String result = address.toString().trim();
            if (!result.isEmpty() && !AADHAAR_ADDRESS_BROKEN_SECTOR.matcher(result).find()) return result;
        }
        return null;
    }

    private String cleanAadhaarAddressLine(String line) {
        String value = line.trim();
        Matcher pin = PINCODE_PATTERN.matcher(value);
        if (pin.find()) value = value.substring(0, pin.end());
        else {
            Matcher footer = AADHAAR_ADDRESS_INLINE_FOOTER.matcher(value);
            if (footer.find()) value = value.substring(0, footer.start());
        }
        return value.replaceAll("[\\s,;|/-]+$", "").trim();
    }

    // ── PAN Parser ──────────────────────────────────────────────────────

    private ExtractionResult parsePan(String text, double confidence) {
        Map<String, String> fields = new HashMap<>();
        Matcher panMatcher = PAN_NUMBER_PATTERN.matcher(text);
        if (!panMatcher.find()) {
            return new ExtractionResult(false, Map.of(), null, confidence,
                    "Could not extract a valid PAN number from the card.");
        }
        String rawPan = panMatcher.group(1).replaceAll("[ \\t-]", "").toUpperCase();
        char holderType = rawPan.charAt(3);
        if (holderType != 'P') {
            String error = OTHER_PAN_HOLDER_TYPES.indexOf(holderType) >= 0
                    ? "Only an individual PAN card can verify a lawyer profile."
                    : "Invalid PAN number: the fourth character is not a valid PAN holder type.";
            return new ExtractionResult(false, Map.of(), null, confidence, error);
        }
        fields.put("panType", "Individual");
        String[] lines = text.split("\\R");
        String name = readPanLabeledValue(lines, PAN_NAME_LABEL_PATTERN);
        String fatherName = readPanLabeledValue(lines, PAN_FATHER_LABEL_PATTERN);
        if (name == null) name = readPanCardholderBeforeFather(lines, fatherName);
        if (name == null || fatherName == null) {
            List<String> orderedNames = readPanNamesBetweenNumberAndDob(lines);
            if (name == null && fatherName == null && orderedNames.size() >= 2) {
                name = orderedNames.get(0);
                fatherName = orderedNames.get(1);
            } else if (name == null && fatherName != null) {
                String knownFather = normalizeName(fatherName);
                name = orderedNames.stream().filter(candidate -> !candidate.equalsIgnoreCase(knownFather))
                        .findFirst().orElse(null);
            } else if (fatherName == null && name != null) {
                String knownName = normalizeName(name);
                fatherName = orderedNames.stream().filter(candidate -> !candidate.equalsIgnoreCase(knownName))
                        .findFirst().orElse(null);
            }
        }
        String dateText = readPanLabeledValue(lines, PAN_DOB_LABEL_PATTERN);
        if (dateText == null) dateText = readPanDateNearBirthLabel(lines);
        if (name != null && isPlausibleName(name)) fields.put("name", normalizeName(name));
        if (fatherName != null && isPlausibleName(fatherName)) {
            fields.put("fatherName", normalizeName(fatherName));
        }
        if (dateText != null) {
            // OCR often puts spaces around separators: "01 / 02 / 1990".
            String normalizedDate = dateText.replaceAll("\\s*([/.\\-])\\s*", "$1");
            Matcher dobMatcher = DOB_PATTERN.matcher(normalizedDate);
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
            for (int j = i + 1; j < lines.length && j <= i + 5; j++) {
                value = stripRepeatedPanLabel(lines[j], labelPattern);
                if (value.isEmpty()) continue;
                if (isPanLabel(value)) break;
                if (isPanFieldValue(value)) return value;
            }
        }
        return null;
    }

    /** Catches a DOB line whose label is surrounded by OCR noise. */
    private String readPanDateNearBirthLabel(String[] lines) {
        Pattern noise = Pattern.compile(
                "(?i).*(?:D[.\\s]?[O0][.\\s]?B\\.?|B[i1l]rth|जन्म[\\s]*तिथि).*");
        for (int i = 0; i < lines.length; i++) {
            if (!noise.matcher(lines[i]).matches()) continue;
            String found = firstDate(lines[i]);
            if (found != null) return found;
            for (int j = i + 1; j < lines.length && j <= i + 3; j++) {
                found = firstDate(lines[j]);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String firstDate(String line) {
        Matcher matcher = DOB_PATTERN.matcher(line.replaceAll("\\s*([/.\\-])\\s*", "$1"));
        return matcher.find() ? matcher.group(1) : null;
    }

    /** The printed cardholder name is commonly the line immediately above Father's Name. */
    private String readPanCardholderBeforeFather(String[] lines, String fatherName) {
        int fatherLabel = -1;
        for (int i = 0; i < lines.length; i++) {
            if (PAN_FATHER_LABEL_PATTERN.matcher(lines[i]).matches()) {
                fatherLabel = i;
                break;
            }
        }
        if (fatherLabel < 0) return null;
        for (int i = fatherLabel - 1; i >= Math.max(0, fatherLabel - 6); i--) {
            // Do not cross the PAN number into unrelated card headings.
            if (PAN_NUMBER_PATTERN.matcher(lines[i]).find()) break;
            String candidate = normalizeName(lines[i]);
            if (isPanPersonNameCandidate(candidate)
                    && !candidate.equalsIgnoreCase(normalizeName(fatherName))) return candidate;
        }
        return null;
    }

    private List<String> readPanNamesBetweenNumberAndDob(String[] lines) {
        int panLine = -1;
        int dobLine = lines.length;
        for (int i = 0; i < lines.length; i++) {
            if (panLine < 0 && PAN_NUMBER_PATTERN.matcher(lines[i]).find()) panLine = i;
            if (panLine >= 0 && PAN_DOB_LABEL_PATTERN.matcher(lines[i]).matches()) {
                dobLine = i;
                break;
            }
        }
        if (panLine < 0 || dobLine <= panLine) return List.of();
        List<String> names = new java.util.ArrayList<>();
        for (int i = panLine + 1; i < Math.min(dobLine, panLine + 12); i++) {
            String candidate = normalizeName(lines[i]);
            if (isPanPersonNameCandidate(candidate)) names.add(candidate);
        }
        return names;
    }

    private boolean isPanPersonNameCandidate(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z .'-]{2,70}")
                && !isPanLabel(value) && !PAN_MARKER_PATTERN.matcher(value).find()
                && !PAN_NUMBER_PATTERN.matcher(value).find()
                && !value.matches("(?i).*\\b(?:name|date|birth|income|tax|department|govt|government|signature|permanent|account|number|india|pan)\\b.*");
    }

    /** Reject OCR garbage like "ar Bt arte" while allowing initials and single names. */
    private boolean isPlausibleName(String value) {
        if (value == null) return false;
        String cleaned = value.trim();
        if (cleaned.length() < 3) return false;
        String[] tokens = cleaned.split("\\s+");
        // PAN cards print English names in uppercase; title case is also acceptable.
        for (String token : tokens) {
            if (!token.matches("[A-Z][A-Za-z.'-]*")) return false;
        }
        if (tokens.length == 1) return tokens[0].length() >= 3;
        long suspicious = 0;
        for (String token : tokens) {
            if (token.length() <= 2 && !(token.length() == 1 && Character.isUpperCase(token.charAt(0)))) {
                suspicious++;
            }
        }
        return suspicious * 2 <= tokens.length;
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

    // ── OCR Response Parsing ────────────────────────────────────────────

    /**
     * Extracts full text string from OCR responses.
     * Supports:
     * 1. Plain String (for tests / manual inputs)
     * 2. Maps with {@code text} or {@code ocrText}
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

            return null;
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

            return 0.85;
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
                .replaceAll("[^\\p{L}\\p{M}\\p{N}\\s.'-]", "")  // preserve Hindi vowel marks
                .replaceAll("\\s+", " ")                    // collapse whitespace
                .replaceAll("^[^\\p{L}]+", "")
                .replaceAll("[^\\p{L}\\p{M}]+$", "")
                .trim();
    }

    private String normalizeWhitespace(String raw) {
        return raw == null ? null : raw.replaceAll("\\s+", " ").trim();
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
