package com.nexoai.ontology.core.agent.llm;

/**
 * A single message in an LLM conversation.
 */
public record LlmMessage(
        Role role,
        String content,
        String toolCallId,
        String toolName
) {

    public enum Role {
        USER, ASSISTANT, SYSTEM, TOOL
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content, null, null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(Role.ASSISTANT, content, null, null);
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(Role.SYSTEM, content, null, null);
    }

    public static LlmMessage toolResult(String toolCallId, String toolName, String content) {
        return new LlmMessage(Role.TOOL, content, toolCallId, toolName);
    }
}
