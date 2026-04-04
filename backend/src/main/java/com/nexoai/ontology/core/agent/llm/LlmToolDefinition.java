package com.nexoai.ontology.core.agent.llm;

import java.util.Map;

/**
 * Definition of a tool/function that the LLM can call.
 */
public record LlmToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema
) {}
