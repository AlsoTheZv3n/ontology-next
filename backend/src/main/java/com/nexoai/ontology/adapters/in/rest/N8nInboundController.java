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
     * n8n creates or upserts a single object in the ontology.
     * If the payload contains an "externalId" field, it will attempt to find and update
     * an existing object with that externalId before creating a new one (Fix 10b).
     */
    @PostMapping("/objects/{objectType}")
    public ResponseEntity<Map<String, Object>> createObjectFromN8n(
            @PathVariable String objectType,
            @RequestBody Map<String, Object> properties,
            @RequestHeader(value = "X-N8N-Webhook-Secret", required = false) String secret) {

        validateWebhookSecret(secret);

        // Fix 10b: Check for externalId in payload for upsert
        String externalId = properties.get("externalId") != null
                ? properties.get("externalId").toString() : null;

        OntologyObject result;
        HttpStatus status;

        if (externalId != null) {
            // Try to find existing object by externalId and update it
            var existing = objectService.findByExternalId(externalId);
            if (existing.isPresent()) {
                JsonNode propsNode = objectMapper.valueToTree(properties);
                result = objectService.updateObjectProperties(existing.get().getId(), propsNode);
                status = HttpStatus.OK;
                log.info("Object upserted (updated) via n8n webhook: {} / externalId={}", objectType, externalId);
            } else {
                JsonNode propsNode = objectMapper.valueToTree(properties);
                result = objectService.createObject(objectType, propsNode);
                status = HttpStatus.CREATED;
                log.info("Object upserted (created) via n8n webhook: {} / externalId={}", objectType, externalId);
            }
        } else {
            JsonNode propsNode = objectMapper.valueToTree(properties);
            result = objectService.createObject(objectType, propsNode);
            status = HttpStatus.CREATED;
            log.info("Object created via n8n webhook: {} / {}", objectType, result.getId());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", result.getId().toString());
        response.put("objectType", result.getObjectTypeName());
        response.put("properties", result.getProperties());
        response.put("createdAt", result.getCreatedAt());
        return ResponseEntity.status(status).body(response);
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
