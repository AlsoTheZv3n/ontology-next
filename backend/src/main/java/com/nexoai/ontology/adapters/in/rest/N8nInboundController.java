package com.nexoai.ontology.adapters.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.service.object.OntologyObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/inbound/n8n")
@RequiredArgsConstructor
@Slf4j
public class N8nInboundController {

    private final OntologyObjectService objectService;
    private final ObjectMapper objectMapper;

    @Value("${nexo.n8n.webhook-secret:nexo-n8n-secret}")
    private String webhookSecret;

    /**
     * n8n creates a single object in the ontology.
     */
    @PostMapping("/objects/{objectType}")
    public ResponseEntity<Map<String, Object>> createObjectFromN8n(
            @PathVariable String objectType,
            @RequestBody Map<String, Object> properties,
            @RequestHeader(value = "X-N8N-Webhook-Secret", required = false) String secret) {

        validateWebhookSecret(secret);

        JsonNode propsNode = objectMapper.valueToTree(properties);
        OntologyObject created = objectService.createObject(objectType, propsNode);

        log.info("Object created via n8n webhook: {} / {}", objectType, created.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("id", created.getId().toString());
        response.put("objectType", created.getObjectTypeName());
        response.put("properties", created.getProperties());
        response.put("createdAt", created.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * n8n sends a callback after workflow completion to update object properties.
     */
    @PostMapping("/callback/{objectId}")
    public ResponseEntity<Void> handleN8nCallback(
            @PathVariable UUID objectId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-N8N-Webhook-Secret", required = false) String secret) {

        validateWebhookSecret(secret);

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedProperties = (Map<String, Object>) payload.get("updatedProperties");
        if (updatedProperties != null) {
            JsonNode propsNode = objectMapper.valueToTree(updatedProperties);
            objectService.updateObjectProperties(objectId, propsNode);
            log.info("Object {} updated via n8n callback", objectId);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * n8n sends a bulk of objects for upsert (e.g. after scraping/ETL).
     */
    @PostMapping("/bulk/{objectType}")
    public ResponseEntity<Map<String, Object>> bulkImportFromN8n(
            @PathVariable String objectType,
            @RequestBody List<Map<String, Object>> records,
            @RequestHeader(value = "X-N8N-Webhook-Secret", required = false) String secret) {

        validateWebhookSecret(secret);

        int created = 0, failed = 0;
        for (Map<String, Object> record : records) {
            try {
                JsonNode propsNode = objectMapper.valueToTree(record);
                objectService.createObject(objectType, propsNode);
                created++;
            } catch (Exception e) {
                log.warn("Failed to upsert record from n8n: {}", e.getMessage());
                failed++;
            }
        }

        log.info("n8n bulk import for {}: {} created, {} failed", objectType, created, failed);
        return ResponseEntity.ok(Map.of(
                "created", created,
                "updated", 0,
                "failed", failed
        ));
    }

    private void validateWebhookSecret(String secret) {
        if (secret == null || !webhookSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
        }
    }
}
