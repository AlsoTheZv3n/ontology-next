package com.nexoai.ontology.core.agent.llm;

import java.util.List;

/**
 * Pluggable LLM provider interface.
 * Implementations wrap different LLM APIs (Anthropic, OpenAI, Ollama, etc.).
 */
public interface LlmProvider {

    /** Human-readable provider name (e.g. "anthropic", "openai"). */
    String getProviderName();

    /** Simple chat without tool calling. */
    LlmResponse chat(String systemPrompt, List<LlmMessage> messages);

    /** Chat with tool/function calling support. */
    LlmResponse chatWithTools(String systemPrompt, List<LlmMessage> messages, List<LlmToolDefinition> tools);

    /** Whether the provider is properly configured and reachable. */
    boolean isAvailable();
}
