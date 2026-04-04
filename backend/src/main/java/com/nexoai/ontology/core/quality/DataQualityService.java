package com.nexoai.ontology.core.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataQualityService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<String> validateObject(UUID objectTypeId, Map<String, Object> properties) {
        List<String> errors = new ArrayList<>();

        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                """
                SELECT * FROM validation_rules
                WHERE object_type_id = ?::uuid AND is_active = TRUE
                """,
                objectTypeId.toString());

        for (Map<String, Object> rule : rules) {
            String propertyName = (String) rule.get("property_name");
            String ruleType = (String) rule.get("rule_type");
            String errorMessage = (String) rule.get("error_message");
            Object value = properties.get(propertyName);

            boolean violated = switch (ruleType) {
                case "REQUIRED" -> value == null || value.toString().isBlank();
                case "MIN_LENGTH" -> {
                    if (value == null) yield false;
                    try {
                        String configStr = rule.get("rule_config").toString();
                        Map<String, Object> config = objectMapper.readValue(configStr, Map.class);
                        int minLen = ((Number) config.get("minLength")).intValue();
                        yield value.toString().length() < minLen;
                    } catch (Exception e) {
                        yield false;
                    }
                }
                case "MAX_LENGTH" -> {
                    if (value == null) yield false;
                    try {
                        String configStr = rule.get("rule_config").toString();
                        Map<String, Object> config = objectMapper.readValue(configStr, Map.class);
                        int maxLen = ((Number) config.get("maxLength")).intValue();
                        yield value.toString().length() > maxLen;
                    } catch (Exception e) {
                        yield false;
                    }
                }
                case "REGEX" -> {
                    if (value == null) yield false;
                    try {
                        String configStr = rule.get("rule_config").toString();
                        Map<String, Object> config = objectMapper.readValue(configStr, Map.class);
                        String pattern = (String) config.get("pattern");
                        yield !value.toString().matches(pattern);
                    } catch (Exception e) {
                        yield false;
                    }
                }
                case "NOT_NULL" -> value == null;
                default -> false;
            };

            if (violated) {
                errors.add(errorMessage);
            }
        }

        return errors;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> runQualityReport(UUID tenantId, UUID objectTypeId) {
        List<Map<String, Object>> objects = jdbcTemplate.queryForList(
                """
                SELECT * FROM ontology_objects
                WHERE tenant_id = ?::uuid AND object_type_id = ?::uuid
                """,
                tenantId.toString(), objectTypeId.toString());

        int totalObjects = objects.size();
        int validObjects = 0;
        List<Map<String, Object>> issues = new ArrayList<>();

        for (Map<String, Object> obj : objects) {
            try {
                String propsStr = obj.get("properties") != null ? obj.get("properties").toString() : "{}";
                Map<String, Object> properties = objectMapper.readValue(propsStr, Map.class);
                List<String> errors = validateObject(objectTypeId, properties);

                if (errors.isEmpty()) {
                    validObjects++;
                } else {
                    Map<String, Object> issue = new HashMap<>();
                    issue.put("objectId", obj.get("id"));
                    issue.put("errors", errors);
                    issues.add(issue);
                }
            } catch (Exception e) {
                Map<String, Object> issue = new HashMap<>();
                issue.put("objectId", obj.get("id"));
                issue.put("errors", List.of("Failed to parse properties: " + e.getMessage()));
                issues.add(issue);
            }
        }

        BigDecimal qualityScore = totalObjects > 0
                ? BigDecimal.valueOf(validObjects * 100.0 / totalObjects).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100.00);

        try {
            UUID reportId = UUID.randomUUID();
            String issuesJson = objectMapper.writeValueAsString(issues);

            jdbcTemplate.update(
                    """
                    INSERT INTO data_quality_reports (id, tenant_id, object_type_id, total_objects, valid_objects, quality_score, issues)
                    VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?::jsonb)
                    """,
                    reportId.toString(), tenantId.toString(), objectTypeId.toString(),
                    totalObjects, validObjects, qualityScore, issuesJson);

            log.info("Quality report for objectType {}: {}/{} valid ({}%)",
                    objectTypeId, validObjects, totalObjects, qualityScore);

            return jdbcTemplate.queryForMap(
                    "SELECT * FROM data_quality_reports WHERE id = ?::uuid", reportId.toString());

        } catch (Exception e) {
            throw new OntologyException("Failed to save quality report: " + e.getMessage());
        }
    }

    public Map<String, Object> getLatestReport(UUID tenantId, UUID objectTypeId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT * FROM data_quality_reports
                    WHERE tenant_id = ?::uuid AND object_type_id = ?::uuid
                    ORDER BY created_at DESC LIMIT 1
                    """,
                    tenantId.toString(), objectTypeId.toString());
        } catch (Exception e) {
            throw new OntologyException("No quality report found for object type: " + objectTypeId);
        }
    }

    public List<Map<String, Object>> getRules(UUID objectTypeId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM validation_rules WHERE object_type_id = ?::uuid ORDER BY created_at",
                objectTypeId.toString());
    }

    public Map<String, Object> addRule(UUID objectTypeId, String propertyName, String ruleType,
                                        String ruleConfig, String errorMessage) {
        UUID ruleId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO validation_rules (id, object_type_id, property_name, rule_type, rule_config, error_message)
                VALUES (?::uuid, ?::uuid, ?, ?, ?::jsonb, ?)
                """,
                ruleId.toString(), objectTypeId.toString(), propertyName, ruleType,
                ruleConfig != null ? ruleConfig : "{}", errorMessage);

        return jdbcTemplate.queryForMap(
                "SELECT * FROM validation_rules WHERE id = ?::uuid", ruleId.toString());
    }
}
