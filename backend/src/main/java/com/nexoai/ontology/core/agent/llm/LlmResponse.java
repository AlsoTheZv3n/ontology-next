package com.nexoai.ontology.core.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * Response from an LLM provider, including optional tool calls.
 */
public record LlmResponse(
        String content,
        List<LlmToolCall> toolCalls,
        String stopReason,
        int inputTokens,
        int outputTokens
) {

    /** A tool/function call requested by the LLM. */
    public record LlmToolCall(
            String id,
            String name,
            Map<String, Object> arguments
    ) {}

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static LlmResponse textOnly(String content, int inputTokens, int outputTokens) {
        return new LlmResponse(content, List.of(), "end_turn", inputTokens, outputTokens);
    }
}
