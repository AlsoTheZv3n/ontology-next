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
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "ollama")
@Slf4j
public class OllamaLlmProvider implements LlmProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${nexo.llm.ollama.model:qwen2.5:7b}")
    private String model;

    public OllamaLlmProvider(
            @Value("${nexo.llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String getProviderName() {
        return "ollama";
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
            body.put("stream", false);

            List<Map<String, Object>> ollamaMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ollamaMessages.add(Map.of("role", "system", "content", systemPrompt));
            }
            for (LlmMessage msg : messages) {
                switch (msg.role()) {
                    case USER -> ollamaMessages.add(Map.of("role", "user", "content", msg.content()));
                    case ASSISTANT -> ollamaMessages.add(Map.of("role", "assistant", "content", msg.content()));
                    case TOOL -> ollamaMessages.add(Map.of("role", "tool", "content", msg.content() != null ? msg.content() : ""));
                    default -> {} // SYSTEM handled above
                }
            }
            body.put("messages", ollamaMessages);

            if (tools != null && !tools.isEmpty()) {
                body.put("tools", tools.stream().map(t -> Map.<String, Object>of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.name(),
                                "description", t.description(),
                                "parameters", t.parametersSchema()
                        )
                )).toList());
            }

            String responseJson = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("Ollama API call failed: {}", e.getMessage());
            return LlmResponse.textOnly("LLM error: " + e.getMessage(), 0, 0);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode message = root.get("message");
            String content = message != null && message.has("content") ? message.get("content").asText() : "";

            List<LlmResponse.LlmToolCall> toolCalls = new ArrayList<>();
            if (message != null && message.has("tool_calls") && message.get("tool_calls").isArray()) {
                for (JsonNode tc : message.get("tool_calls")) {
                    JsonNode fn = tc.get("function");
                    Map<String, Object> args = objectMapper.convertValue(fn.get("arguments"), Map.class);
                    toolCalls.add(new LlmResponse.LlmToolCall(
                            UUID.randomUUID().toString(),
                            fn.get("name").asText(),
                            args != null ? args : Map.of()
                    ));
                }
            }

            int inputTokens = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt(0) : 0;
            int outputTokens = root.has("eval_count") ? root.get("eval_count").asInt(0) : 0;

            return new LlmResponse(content, toolCalls, "stop", inputTokens, outputTokens);
        } catch (Exception e) {
            log.error("Failed to parse Ollama response: {}", e.getMessage());
            return LlmResponse.textOnly("Failed to parse LLM response", 0, 0);
        }
    }
}
