package com.nexoai.ontology.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-run context shared across steps. Holds the trigger payload plus a named
 * slot for each step's output (key: "step_{index}"). A step reads earlier outputs
 * via expressions like "$.triggerData.x" or "$.step_0.matched".
 *
 * Not thread-safe: workflow runs execute steps sequentially.
 */
public class WorkflowContext {

    private final Map<String, JsonNode> data = new LinkedHashMap<>();
    private final ObjectMapper mapper;
    private boolean skipRest;

    public WorkflowContext(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void put(String key, JsonNode value) { data.put(key, value); }
    public JsonNode get(String key) { return data.get(key); }

    public boolean isSkipRest() { return skipRest; }
    public void markSkipRest() { this.skipRest = true; }

    /** Build a combined JSON tree rooted at "$" so expressions can traverse it. */
    public JsonNode root() {
        ObjectNode root = mapper.createObjectNode();
        data.forEach(root::set);
        return root;
    }
}
