package com.nexoai.ontology.core.entityresolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Finds potential duplicates for a given object using three strategies in priority order:
 *
 * 1. EXACT MATCH  — same email/phone/externalId -> confidence 0.99
 * 2. FUZZY MATCH  — Levenshtein distance on name -> confidence 0.75-0.95 depending on distance
 * 3. SEMANTIC     — cosine similarity of property-text embedding (future, not wired here)
 *
 * Keys considered "identity" fields per object: email, phone, vat, tax_id, external_id.
 * Name fields checked for fuzzy: name, displayName, title, full_name.
 *
 * Returns a list of Candidate records with confidence >= 0.75.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityResolutionEngine {

    private final JpaOntologyObjectRepository objectRepository;
    private final ObjectMapper objectMapper;

    private static final List<String> IDENTITY_FIELDS = List.of(
            "email", "phone", "vat", "vat_number", "tax_id", "externalId", "external_id");
    private static final List<String> NAME_FIELDS = List.of(
            "name", "displayName", "display_name", "title", "full_name", "company_name");

    private static final double EXACT_CONFIDENCE = 0.99;
    private static final double MIN_REPORT_CONFIDENCE = 0.75;

    public record Candidate(
            UUID objectId,
            String matchType,      // EXACT | FUZZY | SEMANTIC
            double confidence,     // 0.0 .. 1.0
            Map<String, Object> features
    ) {}

    /**
     * Find duplicate candidates for the given subject object.
     *
     * @param subject the freshly-upserted object
     * @param maxCandidates    maximum candidates to return
     * @return candidates with confidence >= MIN_REPORT_CONFIDENCE, sorted desc
     */
    public List<Candidate> findDuplicates(OntologyObject subject, int maxCandidates) {
        if (subject == null || subject.getObjectTypeId() == null) return List.of();

        JsonNode subjProps = subject.getProperties();
        if (subjProps == null || subjProps.isNull() || subjProps.isEmpty()) return List.of();

        // Narrow candidate set: same object_type, not the subject itself
        List<OntologyObjectEntity> peers = objectRepository.findAll().stream()
                .filter(e -> e.getObjectTypeId().equals(subject.getObjectTypeId()))
                .filter(e -> !e.getId().equals(subject.getId()))
                .toList();

        List<Candidate> candidates = new ArrayList<>();
        for (OntologyObjectEntity peer : peers) {
            JsonNode peerProps = parseProps(peer.getProperties());
            if (peerProps == null) continue;

            Candidate c = scoreAgainst(subject, subjProps, peer, peerProps);
            if (c != null && c.confidence() >= MIN_REPORT_CONFIDENCE) {
                candidates.add(c);
            }
        }

        candidates.sort(Comparator.comparingDouble(Candidate::confidence).reversed());
        if (candidates.size() > maxCandidates) {
            candidates = candidates.subList(0, maxCandidates);
        }
        return candidates;
    }

    private Candidate scoreAgainst(OntologyObject subject, JsonNode subjProps,
                                    OntologyObjectEntity peer, JsonNode peerProps) {
        Map<String, Object> features = new LinkedHashMap<>();

        // 1. Exact match on any identity field
        for (String field : IDENTITY_FIELDS) {
            String a = textValue(subjProps, field);
            String b = textValue(peerProps, field);
            if (a != null && !a.isBlank() && a.equalsIgnoreCase(b)) {
                features.put("exact_field", field);
                features.put("exact_value", a);
                return new Candidate(peer.getId(), "EXACT", EXACT_CONFIDENCE, features);
            }
        }

        // 2. Fuzzy match on any name field — Jaro-Winkler handles abbreviations/prefixes
        //    ("Acme Corporation" vs "Acme Corp") better than plain Levenshtein.
        for (String field : NAME_FIELDS) {
            String a = textValue(subjProps, field);
            String b = textValue(peerProps, field);
            if (a != null && b != null && !a.isBlank() && !b.isBlank()) {
                double sim = jaroWinkler(a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT));
                // Cap fuzzy confidence below EXACT so sorting stays deterministic.
                double capped = Math.min(sim, 0.95);
                if (capped >= MIN_REPORT_CONFIDENCE) {
                    features.put("fuzzy_field", field);
                    features.put("fuzzy_similarity", sim);
                    features.put("a_value", a);
                    features.put("b_value", b);
                    return new Candidate(peer.getId(), "FUZZY", capped, features);
                }
            }
        }

        return null;
    }

    private String textValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private JsonNode parseProps(String propertiesJson) {
        if (propertiesJson == null) return null;
        try {
            return objectMapper.readTree(propertiesJson);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Normalized Levenshtein similarity in [0,1]. 1.0 = identical, 0.0 = fully different.
     */
    static double normalizedLevenshtein(String a, String b) {
        if (a.equals(b)) return 1.0;
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        int dist = levenshtein(a, b);
        return 1.0 - (double) dist / max;
    }

    /**
     * Jaro-Winkler similarity in [0,1]. Gives extra weight to common prefixes, which makes it
     * a better fit for business-name abbreviations ("Acme Corporation" vs "Acme Corp") than
     * plain edit distance.
     */
    static double jaroWinkler(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length(), len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchWindow = Math.max(0, Math.max(len1, len2) / 2 - 1);
        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j]) continue;
                if (s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;

        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }
        transpositions /= 2.0;

        double m = matches;
        double jaro = (m / len1 + m / len2 + (m - transpositions) / m) / 3.0;

        int prefix = 0;
        int prefixMax = Math.min(4, Math.min(len1, len2));
        for (int i = 0; i < prefixMax; i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,       // deletion
                        dp[i][j - 1] + 1),      // insertion
                        dp[i - 1][j - 1] + cost // substitution
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
