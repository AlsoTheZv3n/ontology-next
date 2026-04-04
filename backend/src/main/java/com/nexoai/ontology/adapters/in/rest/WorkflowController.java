package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.tenant.TenantContext;
import com.nexoai.ontology.core.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listWorkflows() {
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(workflowService.listWorkflows(tenantId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createWorkflow(@RequestBody Map<String, Object> request) {
        UUID tenantId = TenantContext.getTenantId();
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        String triggerType = (String) request.get("triggerType");
        String triggerConfig = request.get("triggerConfig") != null ? request.get("triggerConfig").toString() : null;
        String steps = request.get("steps") != null ? request.get("steps").toString() : null;

        Map<String, Object> workflow = workflowService.createWorkflow(tenantId, name, description,
                triggerType, triggerConfig, steps);
        return ResponseEntity.status(HttpStatus.CREATED).body(workflow);
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<Map<String, Object>> triggerWorkflow(@PathVariable UUID id,
                                                                @RequestBody(required = false) Map<String, Object> request) {
        String triggerData = request != null ? request.toString() : null;
        Map<String, Object> run = workflowService.triggerWorkflow(id, triggerData);
        return ResponseEntity.ok(run);
    }

    @GetMapping("/{id}/runs")
    public ResponseEntity<List<Map<String, Object>>> getRunHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getRunHistory(id));
    }
}
