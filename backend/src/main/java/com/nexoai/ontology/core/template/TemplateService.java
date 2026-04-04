package com.nexoai.ontology.core.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listTemplates(String category) {
        if (category != null && !category.isBlank()) {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM object_type_templates WHERE category = ? ORDER BY category, name",
                    category);
        }
        return jdbcTemplate.queryForList(
                "SELECT * FROM object_type_templates ORDER BY category, name");
    }

    public Map<String, Object> getTemplate(UUID id) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT * FROM object_type_templates WHERE id = ?::uuid",
                    id.toString());
        } catch (Exception e) {
            throw new OntologyException("Template not found: " + id);
        }
    }

    public Map<String, Object> importTemplate(UUID tenantId, UUID templateId) {
        Map<String, Object> template = getTemplate(templateId);

        String name = (String) template.get("name");
        String displayName = (String) template.get("display_name");
        String description = (String) template.get("description");
        String icon = (String) template.get("icon");
        String templateDataStr = template.get("template_data").toString();

        try {
            JsonNode templateData = objectMapper.readTree(templateDataStr);

            // Create the object type
            UUID objectTypeId = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO object_types (id, tenant_id, api_name, display_name, description, icon, is_active)
                    VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, TRUE)
                    """,
                    objectTypeId.toString(), tenantId.toString(), name, displayName, description, icon);

            // Create property types from template
            JsonNode properties = templateData.get("properties");
            if (properties != null && properties.isArray()) {
                for (JsonNode prop : properties) {
                    UUID propId = UUID.randomUUID();
                    String apiName = prop.get("apiName").asText();
                    String propDisplayName = prop.get("displayName").asText();
                    String dataType = prop.get("dataType").asText();
                    boolean isPrimaryKey = prop.has("isPrimaryKey") && prop.get("isPrimaryKey").asBoolean();
                    boolean isRequired = prop.has("isRequired") && prop.get("isRequired").asBoolean();

                    jdbcTemplate.update(
                            """
                            INSERT INTO property_types (id, object_type_id, api_name, display_name, data_type,
                                                        is_primary_key, is_required)
                            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)
                            """,
                            propId.toString(), objectTypeId.toString(), apiName, propDisplayName,
                            dataType, isPrimaryKey, isRequired);
                }
            }

            log.info("Imported template '{}' as object type {} for tenant {}", name, objectTypeId, tenantId);

            return jdbcTemplate.queryForMap(
                    "SELECT * FROM object_types WHERE id = ?::uuid", objectTypeId.toString());

        } catch (OntologyException e) {
            throw e;
        } catch (Exception e) {
            throw new OntologyException("Failed to import template: " + e.getMessage());
        }
    }
}
