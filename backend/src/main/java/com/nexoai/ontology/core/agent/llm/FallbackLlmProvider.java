package com.nexoai.ontology.core.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback LLM provider that signals no real LLM is configured.
 * When this is active, AgentSessionService uses the keyword-based routing instead.
 */
@Component
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "none", matchIfMissing = true)
@Slf4j
public class FallbackLlmProvider implements LlmProvider {

    @Override
    public String getProviderName() {
        return "none";
    }

    @Override
    public LlmResponse chat(String systemPrompt, List<LlmMessage> messages) {
        // Return null to signal the caller should use keyword routing
        return null;
    }

    @Override
    public LlmResponse chatWithTools(String systemPrompt, List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        // Return null to signal the caller should use keyword routing
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
