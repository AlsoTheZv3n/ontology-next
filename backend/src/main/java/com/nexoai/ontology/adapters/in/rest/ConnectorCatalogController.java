package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/connector-catalog")
@RequiredArgsConstructor
public class ConnectorCatalogController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        List<Map<String, Object>> connectors = jdbcTemplate.queryForList(
                "SELECT * FROM connector_catalog WHERE is_available = TRUE ORDER BY category, display_name");
        return ResponseEntity.ok(connectors);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        try {
            Map<String, Object> connector = jdbcTemplate.queryForMap(
                    "SELECT * FROM connector_catalog WHERE id = ?", id);
            return ResponseEntity.ok(connector);
        } catch (Exception e) {
            throw new OntologyException("Connector not found: " + id);
        }
    }
}
