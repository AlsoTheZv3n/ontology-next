package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.lineage.LineageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lineage")
@RequiredArgsConstructor
public class LineageController {

    private final LineageService lineageService;

    @GetMapping("/{objectId}")
    public ResponseEntity<List<Map<String, Object>>> getObjectLineage(@PathVariable UUID objectId) {
        return ResponseEntity.ok(lineageService.getObjectLineage(objectId));
    }

    @GetMapping("/{objectId}/{propertyName}")
    public ResponseEntity<List<Map<String, Object>>> getPropertyLineage(@PathVariable UUID objectId,
                                                                         @PathVariable String propertyName) {
        return ResponseEntity.ok(lineageService.getPropertyLineage(objectId, propertyName));
    }
}
