package com.askvocate.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Curated cache of BCI-recognised Indian law institutions.
 *
 * <p>The primary source is the versioned JSON at
 * {@code verification/bci-institutions.json} (colleges, derived universities and
 * manual aliases). An external JSON can be supplied with
 * {@code verification.degree.registry-path} for operator-maintained updates.
 *
 * <p>Matching is normalised substring matching first, then token-window fuzzy
 * matching for OCR variants such as {@code Bharali Vidyapoolh}.
 */
@Component
public class DegreeInstitutionAllowlist {

    private static final Logger log = LoggerFactory.getLogger(DegreeInstitutionAllowlist.class);
    private static final String CLASSPATH_JSON = "verification/bci-institutions.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Entry> entries;
    private final boolean enabled;
    private String registryVersion = "unknown";

    @Autowired
    public DegreeInstitutionAllowlist(
            @Value("${verification.degree.allowlist-enabled:true}") boolean enabled,
            @Value("${verification.degree.registry-path:}") String externalPath) {
        this.enabled = enabled;
        this.entries = load(externalPath);
        log.info("BCI institution registry loaded: {} entries (enabled={})", entries.size(), enabled);
    }

    /** Test/local fallback without Spring property injection. */
    DegreeInstitutionAllowlist() {
        this(true, "");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int size() {
        return entries.size();
    }

    public String registryVersion() {
        return registryVersion;
    }

    /** Compatibility helper: returns the canonical matched name, or null. */
    public String match(String ocrText) {
        Match match = matchInstitution(ocrText);
        return match == null ? null : match.name();
    }

    /**
     * Returns the best registry match with metadata, or {@code null} when nothing
     * clears the exact/fuzzy threshold.
     */
    public Match matchInstitution(String ocrText) {
        if (!enabled) return null;
        String text = normalize(ocrText);
        if (text.isBlank()) return null;

        // Prefer exact institution/university names over affiliation-only matches.
        for (Entry entry : entries) {
            if (entry.normalizedName.length() >= 4 && text.contains(entry.normalizedName)) {
                Entry resolved = entry.approval.equals("Manual alias")
                        ? resolveAlias(entry, entries) : entry;
                String matchType = resolved == entry ? "EXACT" : "ALIAS_RESOLVED";
                return new Match(resolved.name, resolved.state, resolved.courses, resolved.approval,
                        resolved.university, matchType, resolved == entry ? 1.0 : 0.95,
                        entry.normalizedName);
            }
        }
        for (Entry entry : entries) {
            if (entry.normalizedAffiliation.isEmpty() || !text.contains(entry.normalizedAffiliation)) {
                continue;
            }
            Entry university = universityFor(entry.normalizedAffiliation, entries);
            Entry resolved = university != null ? university : entry;
            return new Match(resolved.name, resolved.state, resolved.courses, resolved.approval,
                    resolved.university, "EXACT_AFFILIATION", 1.0, entry.normalizedAffiliation);
        }

        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() >= 6
                    && trimmed.matches("(?i).*\\b(?:university|college|institute|vidyapeeth|"
                    + "vishwavidyalaya|law\\s+school)\\b.*")) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) return null;

        Match best = null;
        double bestScore = 0;
        for (Entry entry : entries) {
            if (entry.normalizedName.length() < 6) continue;
            String[] entryTokens = entry.normalizedName.split(" ");
            for (String line : lines) {
                String[] lineTokens = line.split(" ");
                if (lineTokens.length < entryTokens.length) continue;
                for (int start = 0; start + entryTokens.length <= lineTokens.length; start++) {
                    StringBuilder window = new StringBuilder();
                    for (int offset = 0; offset < entryTokens.length; offset++) {
                        if (offset > 0) window.append(' ');
                        window.append(lineTokens[start + offset]);
                    }
                    if (Math.abs(entry.normalizedName.length() - window.length()) > 30) continue;
                    double score = similarity(entry.normalizedName, window.toString());
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Match(entry.name, entry.state, entry.courses, entry.approval,
                                entry.university, "FUZZY", score, window.toString());
                    }
                }
            }
        }
        return best != null && bestScore >= 0.82 ? best : null;
    }

    public record Match(String name, String state, List<String> courses, String approval,
                        boolean university, String matchType, double score, String matchedText) {
    }

    private record Entry(String name, String normalizedName, String normalizedAffiliation,
                         boolean university, String state, List<String> courses, String approval) {
    }

    private List<Entry> load(String externalPath) {
        InputStream stream = null;
        try {
            if (externalPath != null && !externalPath.isBlank()) {
                stream = Files.newInputStream(Path.of(externalPath.trim()));
                log.info("Loading BCI registry override from {}", externalPath.trim());
            } else {
                stream = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(CLASSPATH_JSON);
            }
            if (stream == null) {
                log.warn("BCI registry resource {} not found", CLASSPATH_JSON);
                return List.of();
            }
            JsonNode root = MAPPER.readTree(stream);
            registryVersion = root.path("version").asText("unknown");
            List<Entry> result = new ArrayList<>();
            addArray(root.path("institutions"), result, false);
            addArray(root.path("universities"), result, true);
            for (JsonNode alias : root.path("manual_aliases")) {
                String value = alias.asText("");
                String normalized = normalize(value);
                if (normalized.length() >= 4) {
                    result.add(new Entry(value, normalized, "", false, "", List.of(), "Manual alias"));
                }
            }
            log.info("BCI registry version={}, source={}, colleges={}, universities={}, aliases={}",
                    root.path("version").asText("unknown"),
                    root.path("source").asText("unknown"),
                    root.path("institutions").size(),
                    root.path("universities").size(),
                    root.path("manual_aliases").size());
            return List.copyOf(result);
        } catch (Exception error) {
            log.error("Could not load BCI institution registry: {}", error.getMessage());
            return List.of();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void addArray(JsonNode array, List<Entry> result, boolean university) {
        if (!array.isArray()) return;
        for (JsonNode node : array) {
            String name = node.path("name").asText("").trim();
            if (name.isBlank()) continue;
            List<String> courses = new ArrayList<>();
            JsonNode courseNodes = node.path("courses");
            if (courseNodes.isArray()) {
                for (JsonNode course : courseNodes) {
                    String value = course.asText("").trim();
                    if (!value.isBlank()) courses.add(value);
                }
            }
            result.add(new Entry(
                    name,
                    normalize(name),
                    normalize(node.path("affiliation").asText("")),
                    university,
                    node.path("state").asText("").trim(),
                    List.copyOf(courses),
                    node.path("bci_approval").asText("").trim()
            ));
        }
    }

    private static Entry universityFor(String affiliation, List<Entry> entries) {
        Entry best = null;
        for (Entry entry : entries) {
            if (!entry.university) continue;
            if (entry.normalizedName.equals(affiliation)
                    || entry.normalizedName.contains(affiliation)
                    || affiliation.contains(entry.normalizedName)) {
                if (best == null || entry.normalizedName.length() > best.normalizedName.length()) {
                    best = entry;
                }
            }
        }
        return best;
    }

    private static Entry resolveAlias(Entry alias, List<Entry> entries) {
        String[] aliasTokens = alias.normalizedName.split(" ");
        Entry best = null;
        int bestMatched = 0;
        for (Entry candidate : entries) {
            if (candidate.approval.equals("Manual alias")) continue;
            int matched = 0;
            for (String token : aliasTokens) {
                if (token.length() < 3) continue;
                if (candidate.normalizedName.contains(token)) matched++;
            }
            if (matched > bestMatched) {
                bestMatched = matched;
                best = candidate;
            }
        }
        int meaningful = 0;
        for (String token : aliasTokens) if (token.length() >= 3) meaningful++;
        return best != null && bestMatched >= Math.max(1, meaningful - 1) ? best : null;
    }

    private static double similarity(String first, String second) {
        int longest = Math.max(first.length(), second.length());
        if (longest == 0) return 1.0;
        return 1.0 - (double) levenshtein(first, second) / longest;
    }

    private static int levenshtein(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) previous[j] = j;
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
