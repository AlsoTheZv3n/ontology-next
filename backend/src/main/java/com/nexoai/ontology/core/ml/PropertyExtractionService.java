package com.nexoai.ontology.core.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured properties from unstructured text using pattern-based NER.
 * This is a lightweight alternative to full DJL-based NER (which requires dslim/bert-base-NER).
 * For production, replace with a HuggingFace model via DJL.
 */
@Service
@Slf4j
public class PropertyExtractionService {

    // Patterns for common entity types
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9][0-9\\s\\-()]{7,}[0-9]");
    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:CHF|EUR|USD|\\$|€)\\s*[0-9][0-9.,]*(?:\\s*(?:Mio|Mrd|K|M|B))?|[0-9][0-9.,]*\\s*(?:CHF|EUR|USD|Mio|Mrd)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-./]+");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}|\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("\\d+[.,]?\\d*\\s*%");

    public Map<String, Object> extractProperties(String rawText, String targetObjectType) {
        if (rawText == null || rawText.isBlank()) return Map.of();

        Map<String, Object> extracted = new LinkedHashMap<>();

        // Extract emails
        extract(EMAIL_PATTERN, rawText, "email", extracted);

        // Extract phone numbers
        extract(PHONE_PATTERN, rawText, "phone", extracted);

        // Extract money values
        Matcher moneyMatcher = MONEY_PATTERN.matcher(rawText);
        if (moneyMatcher.find()) {
            extracted.put("revenue", parseMoneyValue(moneyMatcher.group()));
        }

        // Extract URLs
        extract(URL_PATTERN, rawText, "website", extracted);

        // Extract dates
        extract(DATE_PATTERN, rawText, "date", extracted);

        // Extract percentages
        extract(PERCENTAGE_PATTERN, rawText, "percentage", extracted);

        // Simple name extraction: first capitalized multi-word sequence (likely org/person name)
        Pattern namePattern = Pattern.compile("(?:^|\\s)([A-Z][a-zäöüé]+(?:\\s+[A-Z][a-zäöüé]+)+)");
        Matcher nameMatcher = namePattern.matcher(rawText);
        if (nameMatcher.find()) {
            extracted.put("name", nameMatcher.group(1).strip());
        }

        // Location hints (common Swiss/German cities)
        String lower = rawText.toLowerCase();
        List<String> cities = List.of("zürich", "bern", "basel", "genf", "lausanne", "luzern",
                "berlin", "münchen", "hamburg", "wien", "frankfurt");
        for (String city : cities) {
            if (lower.contains(city)) {
                extracted.put("location", city.substring(0, 1).toUpperCase() + city.substring(1));
                break;
            }
        }

        log.info("Extracted {} properties from text ({} chars)", extracted.size(), rawText.length());
        return extracted;
    }

    private void extract(Pattern pattern, String text, String key, Map<String, Object> result) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            result.put(key, matcher.group().strip());
        }
    }

    private double parseMoneyValue(String moneyString) {
        String cleaned = moneyString.replaceAll("[CHFEURUSDa-zA-Z$€\\s]", "")
                .replace("'", "").replace(",", ".");
        try {
            double value = Double.parseDouble(cleaned);
            String upper = moneyString.toUpperCase();
            if (upper.contains("MRD") || upper.contains("B")) return value * 1_000_000_000;
            if (upper.contains("MIO") || upper.contains("M")) return value * 1_000_000;
            if (upper.contains("K")) return value * 1_000;
            return value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
