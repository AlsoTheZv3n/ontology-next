package com.nexoai.ontology.core.agent.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.agent.llm.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Component
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "anthropic")
@Slf4j
public class AnthropicLlmProvider implements LlmProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${nexo.llm.anthropic.model:claude-sonnet-4-5-20251001}")
    private String model;

    @Value("${nexo.llm.max-tokens:4096}")
    private int maxTokens;

    public AnthropicLlmProvider(
            @Value("${nexo.llm.anthropic.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public LlmResponse chat(String systemPrompt, List<LlmMessage> messages) {
        return chatWithTools(systemPrompt, messages, List.of());
    }

    @Override
    public LlmResponse chatWithTools(String systemPrompt, List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                body.put("system", systemPrompt);
            }
            body.put("messages", convertMessages(messages));
            if (tools != null && !tools.isEmpty()) {
                body.put("tools", convertTools(tools));
            }

            String responseJson = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            return LlmResponse.textOnly("LLM error: " + e.getMessage(), 0, 0);
        }
    }

    @Override
    public boolean isAvailable() {
        return true; // Availability is checked on first call
    }

    private List<Map<String, Object>> convertMessages(List<LlmMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LlmMessage msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            switch (msg.role()) {
                case USER -> {
                    m.put("role", "user");
                    m.put("content", msg.content());
                }
                case ASSISTANT -> {
                    m.put("role", "assistant");
                    m.put("content", msg.content());
                }
                case TOOL -> {
                    m.put("role", "user");
                    m.put("content", List.of(Map.of(
                            "type", "tool_result",
                            "tool_use_id", msg.toolCallId(),
                            "content", msg.content() != null ? msg.content() : ""
                    )));
                }
                default -> {
                    continue; // SYSTEM messages are handled via the system parameter
                }
            }
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> convertTools(List<LlmToolDefinition> tools) {
        return tools.stream().map(t -> {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", t.name());
            tool.put("description", t.description());
            tool.put("input_schema", t.parametersSchema());
            return tool;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            StringBuilder textContent = new StringBuilder();
            List<LlmResponse.LlmToolCall> toolCalls = new ArrayList<>();

            if (root.has("content") && root.get("content").isArray()) {
                for (JsonNode block : root.get("content")) {
                    String type = block.has("type") ? block.get("type").asText() : "";
                    if ("text".equals(type)) {
                        textContent.append(block.get("text").asText());
                    } else if ("tool_use".equals(type)) {
                        Map<String, Object> args = objectMapper.convertValue(
                                block.get("input"), Map.class);
                        toolCalls.add(new LlmResponse.LlmToolCall(
                                block.get("id").asText(),
                                block.get("name").asText(),
                                args != null ? args : Map.of()
                        ));
                    }
                }
            }

            String stopReason = root.has("stop_reason") ? root.get("stop_reason").asText() : "end_turn";
            int inputTokens = root.has("usage") && root.get("usage").has("input_tokens")
                    ? root.get("usage").get("input_tokens").asInt() : 0;
            int outputTokens = root.has("usage") && root.get("usage").has("output_tokens")
                    ? root.get("usage").get("output_tokens").asInt() : 0;

            return new LlmResponse(textContent.toString(), toolCalls, stopReason, inputTokens, outputTokens);
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response: {}", e.getMessage());
            return LlmResponse.textOnly("Failed to parse LLM response", 0, 0);
        }
    }
}
