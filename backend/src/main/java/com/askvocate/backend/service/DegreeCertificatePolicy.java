package com.askvocate.backend.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First-pass rules for Law Degree Certificate (LL.B. / B.A. LL.B.) verification.
 *
 * <p>The policy never replaces manual review for ambiguous cases. It auto-rejects
 * documents that can never satisfy a Bar Council enrollment requirement (wrong
 * degree level/type, marksheets, distance learning) and flags higher-risk cases
 * (foreign degrees, continuing-education programmes, weak name matches, unknown
 * institutions) for an administrator.
 */
@Service
public class DegreeCertificatePolicy {

    public enum Outcome { ACCEPT, REJECT, MANUAL_REVIEW }

    public record Decision(Outcome outcome, String reasonCode, String userMessage,
                           List<String> flags, String detected) {
        public boolean rejected() { return outcome == Outcome.REJECT; }
        public boolean manualReview() { return outcome == Outcome.MANUAL_REVIEW; }

        static Decision accept() {
            return new Decision(Outcome.ACCEPT, null, null, List.of(), null);
        }
    }

    public record DegreeEvidence(String institution, String college, String degreeType,
                                 String courseDuration, String year, String passingYear,
                                 String issueDate) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern MASTER_OF_BUSINESS_LAWS = Pattern.compile(
            "\\b(?:m\\.?\\s*b\\.?\\s*l\\.?|mbl|master\\s+of\\s+business\\s+laws|"
                    + "master\\s+of\\s+legislative\\s+laws?)\\b", FLAGS);
    private static final Pattern MASTER_OF_LAWS = Pattern.compile(
            "\\b(?:l\\.?\\s*l\\.?\\s*m\\.?|ll\\.?\\s*m\\.?|master\\s+of\\s+laws)\\b", FLAGS);
    private static final Pattern WRONG_PROGRAMME = Pattern.compile(
            "\\b(?:diploma|p\\.?\\s*g\\.?\\s+diploma|post[\\s-]*graduate\\s+diploma|"
                    + "advanced\\s+diploma|certificate\\s+course)\\b", FLAGS);
    private static final Pattern INSUFFICIENT_PROOF = Pattern.compile(
            "\\b(?:consolidated\\s+marks?heet|marks?heet|marks?\\s+card|marks?\\s+statement|"
                    + "statement\\s+of\\s+marks|consolidated\\s+statement|transcript|grade\\s+card|"
                    + "grade\\s+sheet|score\\s+card|result\\s+card|admit\\s+card|hall\\s+ticket|"
                    + "university\\s+id|identity\\s+card)\\b|"
                    + "\\bsemester\\s+(?:grade\\s+card|marks?heet|marks?\\s+card|marks?\\s+statement)\\b", FLAGS);
    private static final Pattern INVALID_MODE = Pattern.compile(
            "\\b(?:distance\\s+learning|correspondence|part[\\s-]*time)\\b", FLAGS);
    private static final Pattern CONTINUING_EDUCATION = Pattern.compile(
            "\\b(?:continuing\\s+education|executive\\s+program(?:me)?)\\b", FLAGS);
    private static final Pattern FOREIGN_HINT = Pattern.compile(
            "\\b(?:foreign\\s+university|equivalence\\s+certificate|bci\\s+equivalence)\\b", FLAGS);
    private static final Pattern LLB = Pattern.compile(
            "\\b(?:l\\.?\\s*l\\.?\\s*b\\.?|ll\\.?\\s*b\\.?|bachelor\\s+of\\s+laws)\\b", FLAGS);
    private static final Pattern HINDI_MARKSHEET = Pattern.compile(
            "अंकसूची|अंक\\s*पत्र|मार्कशीट|अंकतालिका");

    /** Countries that never fall under the Indian Bar Council's direct recognition. */
    private static final Pattern FOREIGN_COUNTRY = Pattern.compile("\\b(?:"
            + "ethiopia|nepal|bangladesh|sri\\s+lanka|pakistan|afghanistan|bhutan|myanmar|china|japan|"
            + "south\\s+korea|singapore|malaysia|thailand|vietnam|philippines|indonesia|australia|"
            + "new\\s+zealand|canada|united\\s+states|usa|u\\.s\\.a|united\\s+kingdom|uk|england|"
            + "scotland|wales|ireland|germany|france|italy|spain|netherlands|belgium|switzerland|"
            + "sweden|norway|denmark|finland|poland|russia|ukraine|turkey|united\\s+arab\\s+emirates|"
            + "uae|saudi\\s+arabia|qatar|oman|kuwait|bahrain|south\\s+africa|nigeria|kenya|ghana|"
            + "uganda|tanzania|zambia|zimbabwe|mauritius|fiji|guyana|trinidad"
            + ")\\b", FLAGS);

    private static final String LLB_CERTIFICATE =
            "your Bachelor of Laws (LL.B.) or integrated B.A. LL.B. Final or Provisional Certificate.";

    private final DegreeInstitutionAllowlist allowlist;

    public DegreeCertificatePolicy(DegreeInstitutionAllowlist allowlist) {
        this.allowlist = allowlist;
    }

    /**
     * @param ocrText       combined OCR text from the uploaded certificate
     * @param documentName  name printed on the certificate
     * @param verifiedName  name from the verified Aadhaar/PAN golden record, or null
     * @param confidence    OCR confidence in the range 0..1
     */
    public Decision evaluate(String ocrText, String documentName,
                             String verifiedName, double confidence) {
        return evaluate(ocrText, documentName, verifiedName, confidence, null);
    }

    public Decision evaluate(String ocrText, String documentName, String verifiedName,
                             double confidence, DegreeEvidence evidence) {
        String text = normalize(ocrText);
        if (text.isBlank()) return Decision.accept();

        // Low-confidence text must not drive an irreversible rejection.
        if (confidence > 0 && confidence < 0.4) {
            return manual("DEGREE_LOW_OCR_CONFIDENCE",
                    "We could not read this certificate clearly. Please upload a clearer Final or Provisional Degree Certificate.",
                    List.of("LOW_OCR_CONFIDENCE"), null);
        }

        Matcher match = MASTER_OF_BUSINESS_LAWS.matcher(text);
        if (match.find()) {
            return reject("DEGREE_WRONG_LEVEL",
                    "Invalid Degree Type. You have uploaded a Master of Business Laws (MBL) certificate. "
                            + "To enroll with the Bar Council, you must upload " + LLB_CERTIFICATE,
                    "Master of Business Laws (MBL)");
        }

        match = MASTER_OF_LAWS.matcher(text);
        if (match.find()) {
            return reject("DEGREE_WRONG_LEVEL",
                    "Invalid Degree Type. You have uploaded a Master of Laws (LL.M.) certificate. "
                            + "To enroll with the Bar Council, you must upload " + LLB_CERTIFICATE,
                    "Master of Laws (LL.M.)");
        }

        match = WRONG_PROGRAMME.matcher(text);
        if (match.find()) {
            // OCR can misread seals, watermarks, or bilingual text as a diploma keyword.
            // If the same document also clearly names an LL.B. award, do not hard-reject it;
            // route the conflict for human review. Diploma-only submissions still reject.
            if (LLB.matcher(text).find()) {
                return manual("DEGREE_OCR_CONFLICT",
                        "The scan contains conflicting degree wording. Your LL.B. certificate has been sent for manual review.",
                        List.of("CONFLICTING_DEGREE_OCR"), match.group());
            }
            return reject("DEGREE_WRONG_TYPE",
                    "Invalid Degree Type. Diploma and certificate courses are not accepted. "
                            + "You must upload " + LLB_CERTIFICATE,
                    match.group());
        }

        match = INSUFFICIENT_PROOF.matcher(text);
        boolean insufficientProof = match.find();
        boolean hindiMarksheet = HINDI_MARKSHEET.matcher(ocrText == null ? "" : ocrText).find();
        if (insufficientProof || hindiMarksheet) {
            return reject("DEGREE_MARKSHEET",
                    "Consolidated marksheets are not accepted. Please upload your Final or Provisional Degree Certificate.",
                    insufficientProof ? match.group() : "marksheet");
        }

        match = INVALID_MODE.matcher(text);
        if (match.find()) {
            return reject("DEGREE_INVALID_MODE",
                    "Degrees earned through distance learning, correspondence, or part-time study are not accepted "
                            + "for Bar Council enrollment. Upload your regular LL.B. Final or Provisional Certificate.",
                    match.group());
        }

        match = FOREIGN_COUNTRY.matcher(text);
        boolean foreignCountry = match.find();
        String detectedCountry = foreignCountry ? match.group() : null;
        if (foreignCountry || FOREIGN_HINT.matcher(text).find()) {
            return manual("DEGREE_FOREIGN",
                    "Foreign degrees require a BCI Equivalence Certificate. Your document has been sent for manual review.",
                    List.of("FOREIGN_UNIVERSITY", "REQUIRES_BCI_EQUIVALENCE"),
                    detectedCountry != null ? detectedCountry : "foreign university");
        }

        match = CONTINUING_EDUCATION.matcher(text);
        if (match.find()) {
            return manual("DEGREE_CONTINUING_EDUCATION",
                    "Continuing Education or Executive Programme certificates are not accepted. "
                            + "Your document has been sent for manual review.",
                    List.of("CONTINUING_EDUCATION"), match.group());
        }

        String dateAnomaly = dateAnomaly(evidence);
        if (dateAnomaly != null) {
            return manual("DEGREE_DATE_ANOMALY",
                    "The certificate date or passing year appears to be in the future. "
                            + "Your document has been sent for manual review.",
                    List.of("DATE_ANOMALY"), dateAnomaly);
        }

        if (verifiedName != null && !verifiedName.isBlank()
                && nameSimilarity(documentName, verifiedName) < 0.85) {
            return manual("DEGREE_LOW_NAME_MATCH",
                    "The name on this certificate does not closely match your verified Aadhaar/PAN name. "
                            + "Your document has been sent for manual review.",
                    List.of("LOW_NAME_MATCH"), documentName);
        }

        if (!LLB.matcher(text).find()) {
            return manual("DEGREE_UNCONFIRMED_LLB",
                    "This certificate does not clearly state LL.B. or B.A. LL.B. "
                            + "Your document has been sent for manual review.",
                    List.of("LLB_NOT_DETECTED"), null);
        }

        if (allowlist.isEnabled()) {
            String institutionText = evidence == null ? ""
                    : joined(evidence.institution(), evidence.college());
            DegreeInstitutionAllowlist.Match institutionMatch = allowlist.matchInstitution(
                    institutionText.isBlank() ? text : institutionText + "\n" + text);
            if (institutionMatch == null) {
                return manual("DEGREE_UNRECOGNIZED_UNIVERSITY",
                        "This university is not in our BCI-recognized list. "
                                + "Your document has been sent for manual review.",
                        List.of("UNRECOGNIZED_UNIVERSITY"), null);
            }
            if ("FUZZY".equals(institutionMatch.matchType())) {
                return manual("DEGREE_FUZZY_INSTITUTION_MATCH",
                        "We found a likely university match, but it is not an exact match. "
                                + "Your document has been sent for manual review.",
                        List.of("FUZZY_INSTITUTION_MATCH"), institutionMatch.matchedText());
            }
            Boolean courseOk = courseApproved(institutionMatch, evidence);
            if (Boolean.FALSE.equals(courseOk)) {
                return manual("DEGREE_COURSE_NOT_RECOGNIZED",
                        "This university is BCI-recognized, but this law course is not in the approved "
                                + "course list for that institution. Your document has been sent for manual review.",
                        List.of("COURSE_NOT_RECOGNIZED"), institutionMatch.name());
            }
            Boolean approvalOk = approvalValid(institutionMatch, evidence);
            if (Boolean.FALSE.equals(approvalOk)) {
                return manual("DEGREE_APPROVAL_EXPIRED",
                        "The BCI approval for this institution/course had expired before your degree year. "
                                + "Your document has been sent for manual review.",
                        List.of("APPROVAL_EXPIRED"), institutionMatch.name());
            }
        }

        return Decision.accept();
    }

    /** Registry metadata for persistence/admin display. */
    public DegreeInstitutionAllowlist.Match matchInstitution(String institutionText) {
        return allowlist.matchInstitution(institutionText);
    }

    public String registryVersion() {
        return allowlist.registryVersion();
    }

    /**
     * Returns TRUE/FALSE when the registry has course data, or NULL when it does not.
     * A NULL result means "not enough BCI course data" and does not block auto-approval.
     */
    public Boolean courseApproved(DegreeInstitutionAllowlist.Match match, DegreeEvidence evidence) {
        if (match == null || match.courses() == null || match.courses().isEmpty() || evidence == null) {
            return null;
        }
        Integer requested = durationYears(evidence.courseDuration());
        if (requested == null) requested = durationYears(evidence.degreeType());
        if (requested != null) {
            for (String course : match.courses()) {
                Integer offered = durationYears(course);
                if (requested.equals(offered)) return true;
            }
            return false;
        }
        // Duration not readable: accept if any approved course mentions LL.B.
        for (String course : match.courses()) {
            if (course.toLowerCase(Locale.ROOT).matches(".*\\bll\\.?\\s*b\\b.*")) return true;
        }
        return false;
    }

    /** TRUE/FALSE when an approval-until year is parseable, otherwise NULL. */
    public Boolean approvalValid(DegreeInstitutionAllowlist.Match match, DegreeEvidence evidence) {
        if (match == null || evidence == null) return null;
        Integer validUntil = approvalEndYear(match.approval());
        Integer degreeYear = firstYear(evidence.passingYear() != null ? evidence.passingYear() : evidence.year());
        if (validUntil == null || degreeYear == null) return null;
        return degreeYear <= validUntil;
    }

    private static String joined(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isBlank()) return b;
        if (b.isBlank()) return a;
        return a + "\n" + b;
    }

    private static Integer durationYears(String value) {
        if (value == null) return null;
        String text = value.toLowerCase(Locale.ROOT);
        if (text.matches(".*\\b(?:five|5)\\s*(?:year|yr).*")) return 5;
        if (text.matches(".*\\b(?:three|3)\\s*(?:year|yr).*")) return 3;
        if (text.matches(".*\\bintegrated\\b.*")) return 5;
        return null;
    }

    private static Integer approvalEndYear(String approval) {
        if (approval == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{4})\\s*[-–]\\s*(\\d{2,4})")
                .matcher(approval);
        if (!matcher.find()) return null;
        String start = matcher.group(1);
        String end = matcher.group(2);
        if (end.length() == 4) return Integer.parseInt(end);
        return Integer.parseInt(start.substring(0, 2) + end);
    }

    private static Integer firstYear(String value) {
        if (value == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    private static String dateAnomaly(DegreeEvidence evidence) {
        if (evidence == null) return null;
        LocalDate today = LocalDate.now();
        if (evidence.issueDate() != null && !evidence.issueDate().isBlank()) {
            LocalDate parsed = parseDate(evidence.issueDate());
            if (parsed != null && parsed.isAfter(today)) return evidence.issueDate();
        }
        Integer year = firstYear(evidence.passingYear() != null ? evidence.passingYear() : evidence.year());
        if (year != null && year > today.getYear()) return String.valueOf(year);
        return null;
    }

    private static LocalDate parseDate(String raw) {
        String value = raw.trim().replaceAll("\\s+", "");
        for (String pattern : new String[]{"dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy",
                "yyyy-MM-dd", "yyyy/MM/dd", "d-M-yyyy", "d/M/yyyy"}) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return null;
    }

    private static Decision reject(String code, String message, String detected) {
        return new Decision(Outcome.REJECT, code, message, List.of("AUTO_REJECT"), detected);
    }

    private static Decision manual(String code, String message, List<String> flags, String detected) {
        return new Decision(Outcome.MANUAL_REVIEW, code, message, flags, detected);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\u2010-\\u2015]", "-")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String value) {
        return normalize(value).replaceAll("[^a-z\\s]", "").replaceAll("\\s+", " ").trim();
    }

    /** Similarity in 0..1: best of full-string edit distance and initial-aware token overlap. */
    private static double nameSimilarity(String first, String second) {
        if (first == null || second == null) return 1.0;
        String a = normalizeName(first);
        String b = normalizeName(second);
        if (a.isBlank() || b.isBlank()) return 0.0;
        if (a.equals(b) || a.contains(b) || b.contains(a)) return 1.0;

        int longest = Math.max(a.length(), b.length());
        double editSimilarity = 1.0 - (double) levenshtein(a, b) / longest;

        Set<String> left = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> right = new HashSet<>(Arrays.asList(b.split(" ")));
        Set<String> used = new HashSet<>();
        int common = 0;
        for (String token : left) {
            for (String candidate : right) {
                if (used.contains(candidate)) continue;
                if (tokenMatches(token, candidate)) {
                    used.add(candidate);
                    common++;
                    break;
                }
            }
        }
        double tokenSimilarity = (double) common / Math.min(left.size(), right.size());

        return Math.max(editSimilarity, tokenSimilarity);
    }

    /** Treats an initial as a match for the corresponding full name (e.g. "R" and "Rahul"). */
    private static boolean tokenMatches(String first, String second) {
        if (first.equals(second)) return true;
        if (first.length() == 1 && second.startsWith(first)) return true;
        if (second.length() == 1 && first.startsWith(second)) return true;
        return false;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
