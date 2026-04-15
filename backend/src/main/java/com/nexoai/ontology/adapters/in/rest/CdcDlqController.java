package com.nexoai.ontology.adapters.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.cdc.CdcEventConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin endpoints for the CDC dead-letter queue.
 *
 * Prod-06 wrote failed events to cdc_dead_letter_queue but shipped no way
 * to inspect or re-run them. In practice that meant every failed event
 * was lost unless an operator wrote SQL by hand. This controller exposes
 * the minimum viable workflow: list recent entries, replay one through
 * the CDC consumer, or discard without replay.
 *
 * CdcEventConsumer is conditionally present (@ConditionalOnProperty
 * nexo.cdc.enabled) so the consumer injection uses ObjectProvider to
 * stay safe when CDC is disabled — calling replay in that case returns
 * 503 rather than erroring at startup.
 */
@RestController
@RequestMapping("/api/v1/cdc/dlq")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class CdcDlqController {

    private final JdbcTemplate jdbc;
    private final ObjectProvider<CdcEventConsumer> consumerProvider;
    private final ObjectMapper mapper;
    private final MeterRegistry meterRegistry;

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(defaultValue = "100") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        try {
            return jdbc.queryForList("""
                    SELECT id, record_id, event_data, error_message, created_at
                      FROM cdc_dead_letter_queue
                     ORDER BY created_at DESC
                     LIMIT ?
                    """, capped);
        } catch (Exception e) {
            log.debug("DLQ list failed — table likely absent: {}", e.getMessage());
            return List.of();
        }
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<?> replay(@PathVariable UUID id) {
        CdcEventConsumer consumer = consumerProvider.getIfAvailable();
        if (consumer == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "cdc_disabled",
                    "message", "CdcEventConsumer is not active (nexo.cdc.enabled=false)"));
        }

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT event_data FROM cdc_dead_letter_queue WHERE id = ?::uuid",
                    id.toString());
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        }

        try {
            JsonNode event = mapper.readTree(row.get("event_data").toString());
            String operation = event.path("operation").asText("u");
            String table = event.path("source_table").asText("");
            JsonNode data = event.path("data");
            long tsMs = event.path("ts_ms").asLong(0L);

            consumer.processEvent(operation, table, data.isMissingNode() ? null : data, tsMs);
            jdbc.update("DELETE FROM cdc_dead_letter_queue WHERE id = ?::uuid", id.toString());
            if (meterRegistry != null) {
                meterRegistry.counter("nexo.cdc.dlq.replayed", Tags.of("outcome", "success")).increment();
            }
            return ResponseEntity.ok(Map.of("status", "replayed", "id", id.toString()));
        } catch (Exception e) {
            if (meterRegistry != null) {
                meterRegistry.counter("nexo.cdc.dlq.replayed", Tags.of("outcome", "failure")).increment();
            }
            // Update the error message so next inspection shows the latest failure.
            try {
                jdbc.update(
                        "UPDATE cdc_dead_letter_queue SET error_message = ? WHERE id = ?::uuid",
                        "replay failed: " + e.getMessage(), id.toString());
            } catch (Exception ignored) { /* best effort */ }
            return ResponseEntity.status(500).body(Map.of(
                    "error", "replay_failed",
                    "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> discard(@PathVariable UUID id) {
        int deleted = jdbc.update("DELETE FROM cdc_dead_letter_queue WHERE id = ?::uuid",
                id.toString());
        if (deleted == 0) return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        return ResponseEntity.ok(Map.of("status", "discarded", "id", id.toString()));
    }
}
