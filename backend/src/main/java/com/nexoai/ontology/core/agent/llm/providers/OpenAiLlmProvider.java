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
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "openai")
@Slf4j
public class OpenAiLlmProvider implements LlmProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${nexo.llm.openai.model:gpt-4o}")
    private String model;

    @Value("${nexo.llm.max-tokens:4096}")
    private int maxTokens;

    public OpenAiLlmProvider(
            @Value("${nexo.llm.openai.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String getProviderName() {
        return "openai";
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

            List<Map<String, Object>> oaiMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                oaiMessages.add(Map.of("role", "system", "content", systemPrompt));
            }
            oaiMessages.addAll(convertMessages(messages));
            body.put("messages", oaiMessages);

            if (tools != null && !tools.isEmpty()) {
                body.put("tools", convertTools(tools));
            }

            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            return LlmResponse.textOnly("LLM error: " + e.getMessage(), 0, 0);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
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
                    m.put("role", "tool");
                    m.put("tool_call_id", msg.toolCallId());
                    m.put("content", msg.content() != null ? msg.content() : "");
                }
                default -> {
                    continue;
                }
            }
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> convertTools(List<LlmToolDefinition> tools) {
        return tools.stream().map(t -> Map.<String, Object>of(
                "type", "function",
                "function", Map.of(
                        "name", t.name(),
                        "description", t.description(),
                        "parameters", t.parametersSchema()
                )
        )).toList();
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return LlmResponse.textOnly("No response from OpenAI", 0, 0);
            }

            JsonNode message = choices.get(0).get("message");
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText() : "";

            List<LlmResponse.LlmToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                for (JsonNode tc : message.get("tool_calls")) {
                    JsonNode fn = tc.get("function");
                    Map<String, Object> args = objectMapper.readValue(
                            fn.get("arguments").asText(), Map.class);
                    toolCalls.add(new LlmResponse.LlmToolCall(
                            tc.get("id").asText(),
                            fn.get("name").asText(),
                            args
                    ));
                }
            }

            String stopReason = choices.get(0).has("finish_reason")
                    ? choices.get(0).get("finish_reason").asText() : "stop";
            int inputTokens = root.has("usage") ? root.get("usage").get("prompt_tokens").asInt(0) : 0;
            int outputTokens = root.has("usage") ? root.get("usage").get("completion_tokens").asInt(0) : 0;

            return new LlmResponse(content, toolCalls, stopReason, inputTokens, outputTokens);
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", e.getMessage());
            return LlmResponse.textOnly("Failed to parse LLM response", 0, 0);
        }
    }
}
