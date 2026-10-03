package com.askvocate.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates, parses, and normalizes Indian State Bar Council enrollment numbers.
 * 
 * <p>Standard Indian Bar Council Enrollment Number format:
 * {@code [STATE_CODE]/[SERIAL_NUMBER]/[YEAR]}
 * 
 * Examples:
 * <ul>
 *   <li>{@code D/1234/2021} — Bar Council of Delhi</li>
 *   <li>{@code MAH/5678/2019} — Bar Council of Maharashtra & Goa</li>
 *   <li>{@code UP/892/2018} — Bar Council of Uttar Pradesh</li>
 *   <li>{@code KAR/12345/2020} — Bar Council of Karnataka</li>
 * </ul>
 */
@Service
public class BarCouncilValidationService {

    private static final Logger log = LoggerFactory.getLogger(BarCouncilValidationService.class);

    // Primary regex for Bar Council Enrollment Number
    // Matches patterns like D/1234/2021, MAH-5678-2019, UP/123/21, KAR.1234.2022
    public static final Pattern ENROLLMENT_PATTERN = Pattern.compile(
            "^(?i)([A-Z]{1,4})\\s*[/\\-.]\\s*(\\d{1,7})\\s*[/\\-.]\\s*(\\d{2}|(?:19|20)\\d{2})$"
    );

    // Embedded regex to locate enrollment numbers in freeform OCR text
    public static final Pattern EMBEDDED_ENROLLMENT_PATTERN = Pattern.compile(
            "(?i)\\b([A-Z]{1,4}\\s*[/\\-.]\\s*\\d{1,7}\\s*[/\\-.]\\s*(?:19|20)?\\d{2})\\b"
    );

    // State code to official State Name mapping
    private static final Map<String, String> STATE_NAMES = new LinkedHashMap<>();
    // State Name to list of valid State Bar Council codes
    private static final Map<String, List<String>> STATE_TO_CODES = new LinkedHashMap<>();

    static {
        STATE_BAR_COUNCILS.put("D", "Bar Council of Delhi");
        STATE_BAR_COUNCILS.put("DL", "Bar Council of Delhi");
        STATE_BAR_COUNCILS.put("MAH", "Bar Council of Maharashtra & Goa");
        STATE_BAR_COUNCILS.put("MH", "Bar Council of Maharashtra & Goa");
        STATE_BAR_COUNCILS.put("UP", "Bar Council of Uttar Pradesh");
        STATE_BAR_COUNCILS.put("KAR", "Bar Council of Karnataka");
        STATE_BAR_COUNCILS.put("KA", "Bar Council of Karnataka");
        STATE_BAR_COUNCILS.put("TN", "Bar Council of Tamil Nadu & Puducherry");
        STATE_BAR_COUNCILS.put("WB", "Bar Council of West Bengal");
        STATE_BAR_COUNCILS.put("P", "Bar Council of Punjab & Haryana");
        STATE_BAR_COUNCILS.put("PH", "Bar Council of Punjab & Haryana");
        STATE_BAR_COUNCILS.put("MP", "Bar Council of Madhya Pradesh");
        STATE_BAR_COUNCILS.put("GJ", "Bar Council of Gujarat");
        STATE_BAR_COUNCILS.put("G", "Bar Council of Gujarat");
        STATE_BAR_COUNCILS.put("AP", "Bar Council of Andhra Pradesh");
        STATE_BAR_COUNCILS.put("TS", "Bar Council of Telangana");
        STATE_BAR_COUNCILS.put("TG", "Bar Council of Telangana");
        STATE_BAR_COUNCILS.put("BR", "Bar Council of Bihar");
        STATE_BAR_COUNCILS.put("BH", "Bar Council of Bihar");
        STATE_BAR_COUNCILS.put("JH", "Bar Council of Jharkhand");
        STATE_BAR_COUNCILS.put("OR", "Bar Council of Odisha");
        STATE_BAR_COUNCILS.put("OD", "Bar Council of Odisha");
        STATE_BAR_COUNCILS.put("RJ", "Bar Council of Rajasthan");
        STATE_BAR_COUNCILS.put("R", "Bar Council of Rajasthan");
        STATE_BAR_COUNCILS.put("KL", "Bar Council of Kerala");
        STATE_BAR_COUNCILS.put("K", "Bar Council of Kerala");
        STATE_BAR_COUNCILS.put("UK", "Bar Council of Uttarakhand");
        STATE_BAR_COUNCILS.put("UA", "Bar Council of Uttarakhand");
        STATE_BAR_COUNCILS.put("HP", "Bar Council of Himachal Pradesh");
        STATE_BAR_COUNCILS.put("AS", "Bar Council of Assam, Nagaland, Mizoram, Arunachal Pradesh & Sikkim");
        STATE_BAR_COUNCILS.put("NE", "Bar Council of Assam, Nagaland, Mizoram, Arunachal Pradesh & Sikkim");
        STATE_BAR_COUNCILS.put("CG", "Bar Council of Chhattisgarh");
        STATE_BAR_COUNCILS.put("CH", "Bar Council of Chhattisgarh");
        STATE_BAR_COUNCILS.put("BCI", "Bar Council of India");

        STATE_NAMES.put("D", "Delhi");
        STATE_NAMES.put("DL", "Delhi");
        STATE_NAMES.put("MAH", "Maharashtra");
        STATE_NAMES.put("MH", "Maharashtra");
        STATE_NAMES.put("UP", "Uttar Pradesh");
        STATE_NAMES.put("KAR", "Karnataka");
        STATE_NAMES.put("KA", "Karnataka");
        STATE_NAMES.put("TN", "Tamil Nadu");
        STATE_NAMES.put("WB", "West Bengal");
        STATE_NAMES.put("P", "Punjab & Haryana");
        STATE_NAMES.put("PH", "Punjab & Haryana");
        STATE_NAMES.put("MP", "Madhya Pradesh");
        STATE_NAMES.put("GJ", "Gujarat");
        STATE_NAMES.put("G", "Gujarat");
        STATE_NAMES.put("AP", "Andhra Pradesh");
        STATE_NAMES.put("TS", "Telangana");
        STATE_NAMES.put("TG", "Telangana");
        STATE_NAMES.put("BR", "Bihar");
        STATE_NAMES.put("BH", "Bihar");
        STATE_NAMES.put("JH", "Jharkhand");
        STATE_NAMES.put("OR", "Odisha");
        STATE_NAMES.put("OD", "Odisha");
        STATE_NAMES.put("RJ", "Rajasthan");
        STATE_NAMES.put("R", "Rajasthan");
        STATE_NAMES.put("KL", "Kerala");
        STATE_NAMES.put("K", "Kerala");
        STATE_NAMES.put("UK", "Uttarakhand");
        STATE_NAMES.put("UA", "Uttarakhand");
        STATE_NAMES.put("HP", "Himachal Pradesh");
        STATE_NAMES.put("AS", "Assam");
        STATE_NAMES.put("NE", "North East");
        STATE_NAMES.put("CG", "Chhattisgarh");
        STATE_NAMES.put("CH", "Chhattisgarh");
        STATE_NAMES.put("BCI", "National (India)");

        // Populate reverse map (State -> Bar Council codes)
        STATE_NAMES.forEach((code, state) ->
                STATE_TO_CODES.computeIfAbsent(state.toLowerCase(), k -> new ArrayList<>()).add(code)
        );
    }

    /**
     * Validates and parses a Bar Council enrollment number string.
     */
    public BarCouncilValidationResult validate(String rawEnrollmentNumber) {
        if (rawEnrollmentNumber == null || rawEnrollmentNumber.isBlank()) {
            return BarCouncilValidationResult.failure("Bar Council enrollment number cannot be empty.");
        }

        String cleaned = rawEnrollmentNumber.trim().replaceAll("\\s+", "");
        Matcher matcher = ENROLLMENT_PATTERN.matcher(cleaned);

        if (!matcher.matches()) {
            return BarCouncilValidationResult.failure(
                    "Invalid Bar Council enrollment number format: '" + rawEnrollmentNumber
                    + "'. Expected format e.g. D/1234/2021 or MAH/5678/2019."
            );
        }

        String stateCode = matcher.group(1).toUpperCase();
        String sequenceNumber = matcher.group(2);
        String yearStr = matcher.group(3);

        // Normalize year to 4-digit
        int currentYear = Year.now().getValue();
        int year;
        try {
            if (yearStr.length() == 2) {
                int twoDigit = Integer.parseInt(yearStr);
                year = (twoDigit <= (currentYear % 100)) ? 2000 + twoDigit : 1900 + twoDigit;
            } else {
                year = Integer.parseInt(yearStr);
            }
        } catch (NumberFormatException e) {
            return BarCouncilValidationResult.failure("Invalid year in enrollment number: " + yearStr);
        }

        if (year < 1950 || year > currentYear + 1) {
            return BarCouncilValidationResult.failure(
                    "Invalid enrollment year: " + year + ". Must be between 1950 and " + (currentYear + 1) + "."
            );
        }

        try {
            long seq = Long.parseLong(sequenceNumber);
            if (seq <= 0) {
                return BarCouncilValidationResult.failure("Invalid sequence number: " + sequenceNumber);
            }
        } catch (NumberFormatException e) {
            return BarCouncilValidationResult.failure("Invalid sequence number format: " + sequenceNumber);
        }

        // Lookup State & Bar Council Name
        String stateCouncil = STATE_BAR_COUNCILS.getOrDefault(stateCode, "State Bar Council (" + stateCode + ")");
        String stateName = STATE_NAMES.getOrDefault(stateCode, stateCode);
        String country = "India";
        String normalized = stateCode + "/" + sequenceNumber + "/" + year;
        String masked = maskBarCouncilNumber(stateCode, sequenceNumber, year);

        return BarCouncilValidationResult.success(
                normalized,
                masked,
                stateCode,
                stateCouncil,
                stateName,
                country,
                sequenceNumber,
                year
        );
    }

    /**
     * Resolves State name from Bar Council code.
     */
    public String getStateFromCode(String stateCode) {
        if (stateCode == null) return null;
        return STATE_NAMES.getOrDefault(stateCode.toUpperCase().trim(), stateCode);
    }

    /**
     * Resolves valid Bar Council code(s) from a State name.
     */
    public List<String> getCodesFromState(String stateName) {
        if (stateName == null || stateName.isBlank()) return List.of();
        return STATE_TO_CODES.getOrDefault(stateName.toLowerCase().trim(), List.of());
    }

    /**
     * Masks the Bar Council enrollment number, showing state and year but obscuring sequence digits.
     * Example: MAH/5678/2019 to MAH/XXXX/2019
     */
    public String maskBarCouncilNumber(String stateCode, String sequenceNumber, int year) {
        String mask = "X".repeat(Math.max(4, sequenceNumber.length()));
        return stateCode + "/" + mask + "/" + year;
    }

    /**
     * Checks whether an advocate name on a document matches the registered lawyer profile name.
     * Tolerates titles ("Adv.", "Advocate", "Mr.", "Ms.", "Dr."), honorifics, and word order differences.
     */
    public boolean isNameMatching(String documentName, String profileName) {
        if (documentName == null || profileName == null) {
            return false;
        }

        String normDoc = cleanName(documentName);
        String normProfile = cleanName(profileName);

        if (normDoc.equalsIgnoreCase(normProfile)) {
            return true;
        }

        if (normDoc.contains(normProfile) || normProfile.contains(normDoc)) {
            return true;
        }

        Set<String> docTokens = new HashSet<>(Arrays.asList(normDoc.split("\\s+")));
        Set<String> profileTokens = new HashSet<>(Arrays.asList(normProfile.split("\\s+")));

        Set<String> intersection = new HashSet<>(docTokens);
        intersection.retainAll(profileTokens);

        int minTokens = Math.min(docTokens.size(), profileTokens.size());
        return minTokens > 0 && ((double) intersection.size() / minTokens) >= 0.6;
    }

    private String cleanName(String name) {
        return name.toLowerCase()
                .replaceAll("(?i)\\b(advocate|adv\\.?|mr\\.?|mrs\\.?|ms\\.?|shri|smt|dr\\.?)\\b", "")
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record BarCouncilValidationResult(
            boolean valid,
            String normalizedNumber,
            String maskedNumber,
            String stateCode,
            String stateCouncil,
            String stateName,
            String country,
            String sequenceNumber,
            Integer enrollmentYear,
            String errorMessage
    ) {
        public static BarCouncilValidationResult success(
                String normalized, String masked, String stateCode,
                String stateCouncil, String stateName, String country,
                String sequenceNumber, int year) {
            return new BarCouncilValidationResult(
                    true, normalized, masked, stateCode, stateCouncil, stateName, country, sequenceNumber, year, null
            );
        }

        public static BarCouncilValidationResult failure(String errorMessage) {
            return new BarCouncilValidationResult(
                    false, null, null, null, null, null, null, null, null, errorMessage
            );
        }
    }
}
