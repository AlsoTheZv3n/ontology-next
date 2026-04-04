# Fix 03 — Pluggable LLM Provider

**Priorität:** 🔴 Kritisch  
**Aufwand:** Medium  
**Status:** 🔲 Offen

---

## Ziel

Der AI Agent soll per Konfiguration mit jedem LLM Provider funktionieren — ohne Code-Änderung. Wer Anthropic Claude will: API-Key setzen und `NEXO_LLM_PROVIDER=anthropic`. Wer OpenAI will: `NEXO_LLM_PROVIDER=openai`. Wer Qwen, Mistral, oder ein lokales Modell via Ollama will: dasselbe Prinzip.

---

## Unterstützte Provider

| Provider | Modell | Anmerkung |
|---|---|---|
| `anthropic` | claude-sonnet-4-5 (default) | Empfohlen — bestes Reasoning |
| `openai` | gpt-4o (default) | Weit verbreitet |
| `ollama` | qwen2.5, llama3, mistral, etc. | Lokal, kein API-Key nötig |
| `openai-compatible` | Qwen via DashScope, Together AI, Groq | OpenAI-kompatibler Endpoint |

---

## Files

```
backend/src/main/java/com/nexoai/ontology/core/agent/
├── llm/
│   ├── LlmProvider.java                    ← Interface (neu)
│   ├── LlmMessage.java                     ← Record (neu)
│   ├── LlmResponse.java                    ← Record (neu)
│   ├── LlmProviderFactory.java             ← Factory (neu)
│   ├── providers/
│   │   ├── AnthropicLlmProvider.java       ← Implementierung (neu)
│   │   ├── OpenAiLlmProvider.java          ← Implementierung (neu)
│   │   ├── OllamaLlmProvider.java          ← Implementierung (neu)
│   │   └── OpenAiCompatibleLlmProvider.java← Implementierung (neu)
├── AgentSessionService.java                ← refactoren
├── OntologyQueryPlanner.java               ← refactoren
└── OntologyAgentTools.java                 ← bleibt

backend/src/main/resources/application.yml  ← LLM Config hinzufügen
```

---

## Kernkonzept: LlmProvider Interface

```java
package com.nexoai.ontology.core.agent.llm;

/**
 * Abstrahiert jeden LLM-Provider.
 * Jede Implementierung übersetzt den generischen Request
 * in den Provider-spezifischen API-Aufruf.
 */
public interface LlmProvider {

    /**
     * Provider-Name für Logs und Config ("anthropic", "openai", "ollama")
     */
    String getProviderName();

    /**
     * Einfacher Chat-Completion ohne Tool-Use
     */
    LlmResponse chat(String systemPrompt, List<LlmMessage> messages);

    /**
     * Chat mit Tool-Use / Function-Calling
     * Tools sind als JSON-Schema definiert
     */
    LlmResponse chatWithTools(
        String systemPrompt,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools
    );

    /**
     * Prüft ob Provider erreichbar ist
     */
    boolean isAvailable();
}
```

---

## LlmMessage.java

```java
package com.nexoai.ontology.core.agent.llm;

public record LlmMessage(
    Role role,
    String content,
    String toolCallId,      // Für Tool-Result Messages
    String toolName         // Für Tool-Call Messages
) {
    public enum Role { USER, ASSISTANT, SYSTEM, TOOL }

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content, null, null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(Role.ASSISTANT, content, null, null);
    }

    public static LlmMessage toolResult(String toolCallId, String content) {
        return new LlmMessage(Role.TOOL, content, toolCallId, null);
    }
}
```

---

## LlmResponse.java

```java
public record LlmResponse(
    String content,             // Text-Antwort des LLM
    List<LlmToolCall> toolCalls, // Tool-Calls die das LLM machen will
    String stopReason,          // "end_turn", "tool_use", "max_tokens"
    int inputTokens,
    int outputTokens
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public record LlmToolCall(
        String id,
        String name,
        Map<String, Object> arguments
    ) {}
}
```

---

## LlmToolDefinition.java

```java
public record LlmToolDefinition(
    String name,
    String description,
    Map<String, Object> parametersSchema   // JSON Schema
) {}
```

---

## Provider 1: Anthropic

```java
package com.nexoai.ontology.core.agent.llm.providers;

@Component
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "anthropic")
@Slf4j
public class AnthropicLlmProvider implements LlmProvider {

    private final WebClient webClient;
    private final String model;
    private final int maxTokens;

    public AnthropicLlmProvider(
        @Value("${nexo.llm.anthropic.api-key}") String apiKey,
        @Value("${nexo.llm.anthropic.model:claude-sonnet-4-5-20251001}") String model,
        @Value("${nexo.llm.max-tokens:4096}") int maxTokens
    ) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public String getProviderName() { return "anthropic"; }

    @Override
    public LlmResponse chat(String systemPrompt, List<LlmMessage> messages) {
        return chatWithTools(systemPrompt, messages, List.of());
    }

    @Override
    public LlmResponse chatWithTools(
        String systemPrompt,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools
    ) {
        Map<String, Object> requestBody = buildRequest(systemPrompt, messages, tools);

        Map<String, Object> response = webClient.post()
            .uri("/v1/messages")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block(Duration.ofSeconds(60));

        return parseAnthropicResponse(response);
    }

    private Map<String, Object> buildRequest(
        String systemPrompt,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        body.put("messages", messages.stream()
            .filter(m -> m.role() != LlmMessage.Role.SYSTEM)
            .map(this::toAnthropicMessage)
            .toList());

        if (!tools.isEmpty()) {
            body.put("tools", tools.stream().map(t -> Map.of(
                "name", t.name(),
                "description", t.description(),
                "input_schema", t.parametersSchema()
            )).toList());
        }

        return body;
    }

    private Map<String, Object> toAnthropicMessage(LlmMessage msg) {
        return switch (msg.role()) {
            case USER -> Map.of("role", "user", "content", msg.content());
            case ASSISTANT -> Map.of("role", "assistant", "content", msg.content());
            case TOOL -> Map.of(
                "role", "user",
                "content", List.of(Map.of(
                    "type", "tool_result",
                    "tool_use_id", msg.toolCallId(),
                    "content", msg.content()
                ))
            );
            default -> throw new IllegalArgumentException("Unsupported role: " + msg.role());
        };
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseAnthropicResponse(Map<String, Object> response) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");

        String text = null;
        List<LlmResponse.LlmToolCall> toolCalls = new ArrayList<>();

        for (Map<String, Object> block : content) {
            String type = (String) block.get("type");
            if ("text".equals(type)) {
                text = (String) block.get("text");
            } else if ("tool_use".equals(type)) {
                toolCalls.add(new LlmResponse.LlmToolCall(
                    (String) block.get("id"),
                    (String) block.get("name"),
                    (Map<String, Object>) block.get("input")
                ));
            }
        }

        return new LlmResponse(
            text,
            toolCalls,
            (String) response.get("stop_reason"),
            ((Number) usage.get("input_tokens")).intValue(),
            ((Number) usage.get("output_tokens")).intValue()
        );
    }

    @Override
    public boolean isAvailable() {
        try {
            webClient.get().uri("/v1/models").retrieve().toBodilessEntity().block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## Provider 2: OpenAI

```java
@Component
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "openai")
@Slf4j
public class OpenAiLlmProvider implements LlmProvider {

    private final WebClient webClient;
    private final String model;
    private final int maxTokens;

    public OpenAiLlmProvider(
        @Value("${nexo.llm.openai.api-key}") String apiKey,
        @Value("${nexo.llm.openai.model:gpt-4o}") String model,
        @Value("${nexo.llm.max-tokens:4096}") int maxTokens
    ) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder()
            .baseUrl("https://api.openai.com")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public String getProviderName() { return "openai"; }

    @Override
    public LlmResponse chatWithTools(
        String systemPrompt,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools
    ) {
        List<Map<String, Object>> allMessages = new ArrayList<>();
        allMessages.add(Map.of("role", "system", "content", systemPrompt));
        allMessages.addAll(messages.stream().map(this::toOpenAiMessage).toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("messages", allMessages);

        if (!tools.isEmpty()) {
            body.put("tools", tools.stream().map(t -> Map.of(
                "type", "function",
                "function", Map.of(
                    "name", t.name(),
                    "description", t.description(),
                    "parameters", t.parametersSchema()
                )
            )).toList());
        }

        Map<String, Object> response = webClient.post()
            .uri("/v1/chat/completions")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block(Duration.ofSeconds(60));

        return parseOpenAiResponse(response);
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseOpenAiResponse(Map<String, Object> response) {
        Map<String, Object> choice = ((List<Map<String, Object>>) response.get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");

        String content = (String) message.get("content");
        List<LlmResponse.LlmToolCall> toolCalls = new ArrayList<>();

        List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (rawToolCalls != null) {
            for (Map<String, Object> tc : rawToolCalls) {
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                String argsJson = (String) func.get("arguments");
                toolCalls.add(new LlmResponse.LlmToolCall(
                    (String) tc.get("id"),
                    (String) func.get("name"),
                    objectMapper.readValue(argsJson, new TypeReference<>() {})
                ));
            }
        }

        return new LlmResponse(
            content,
            toolCalls,
            (String) choice.get("finish_reason"),
            ((Number) usage.get("prompt_tokens")).intValue(),
            ((Number) usage.get("completion_tokens")).intValue()
        );
    }
}
```

---

## Provider 3: Ollama (Qwen, Llama, Mistral, etc.)

```java
@Component
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "ollama")
@Slf4j
public class OllamaLlmProvider implements LlmProvider {

    private final WebClient webClient;
    private final String model;

    public OllamaLlmProvider(
        @Value("${nexo.llm.ollama.base-url:http://localhost:11434}") String baseUrl,
        @Value("${nexo.llm.ollama.model:qwen2.5:7b}") String model
    ) {
        this.model = model;
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    public String getProviderName() { return "ollama"; }

    @Override
    public LlmResponse chatWithTools(
        String systemPrompt,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools
    ) {
        // Ollama nutzt OpenAI-kompatibles Format
        List<Map<String, Object>> allMessages = new ArrayList<>();
        allMessages.add(Map.of("role", "system", "content", systemPrompt));
        allMessages.addAll(messages.stream().map(m -> Map.of(
            "role", m.role().name().toLowerCase(),
            "content", m.content()
        )).toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", allMessages);
        body.put("stream", false);

        // Tools: Ollama unterstützt Function Calling ab v0.3+
        if (!tools.isEmpty()) {
            body.put("tools", tools.stream().map(t -> Map.of(
                "type", "function",
                "function", Map.of(
                    "name", t.name(),
                    "description", t.description(),
                    "parameters", t.parametersSchema()
                )
            )).toList());
        }

        Map<String, Object> response = webClient.post()
            .uri("/api/chat")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block(Duration.ofSeconds(120));   // Lokale Modelle brauchen länger

        return parseOllamaResponse(response);
    }

    @Override
    public boolean isAvailable() {
        try {
            webClient.get().uri("/api/tags").retrieve().toBodilessEntity().block(Duration.ofSeconds(3));
            return true;
        } catch (Exception e) {
            log.warn("Ollama not reachable at configured URL");
            return false;
        }
    }
}
```

---

## Provider 4: OpenAI-Compatible (Qwen via DashScope, Groq, Together AI)

```java
@Component
@ConditionalOnProperty(name = "nexo.llm.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleLlmProvider extends OpenAiLlmProvider {

    public OpenAiCompatibleLlmProvider(
        @Value("${nexo.llm.openai-compatible.base-url}") String baseUrl,
        @Value("${nexo.llm.openai-compatible.api-key}") String apiKey,
        @Value("${nexo.llm.openai-compatible.model}") String model,
        @Value("${nexo.llm.max-tokens:4096}") int maxTokens
    ) {
        // Überschreibt nur die Base-URL → gleiche Logik wie OpenAI
        super(baseUrl, apiKey, model, maxTokens);
    }

    @Override
    public String getProviderName() { return "openai-compatible"; }
}
```

---

## LlmProviderFactory.java

```java
@Component
@Slf4j
public class LlmProviderFactory {

    private final LlmProvider activeProvider;

    public LlmProviderFactory(List<LlmProvider> providers,
                               @Value("${nexo.llm.provider}") String configuredProvider) {
        this.activeProvider = providers.stream()
            .filter(p -> p.getProviderName().equals(configuredProvider))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No LLM provider found for: " + configuredProvider +
                ". Available: " + providers.stream().map(LlmProvider::getProviderName).toList()
            ));

        log.info("Active LLM Provider: {} (available: {})",
            activeProvider.getProviderName(),
            activeProvider.isAvailable() ? "YES" : "NO — check config"
        );
    }

    public LlmProvider getProvider() {
        return activeProvider;
    }
}
```

---

## application.yml — vollständige LLM-Konfiguration

```yaml
nexo:
  llm:
    # Aktiver Provider: anthropic | openai | ollama | openai-compatible
    provider: ${NEXO_LLM_PROVIDER:anthropic}
    max-tokens: ${NEXO_LLM_MAX_TOKENS:4096}

    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      model: ${ANTHROPIC_MODEL:claude-sonnet-4-5-20251001}

    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_MODEL:gpt-4o}

    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: ${OLLAMA_MODEL:qwen2.5:7b}   # oder llama3.2, mistral, etc.

    openai-compatible:
      base-url: ${LLM_COMPATIBLE_BASE_URL:}    # z.B. https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${LLM_COMPATIBLE_API_KEY:}
      model: ${LLM_COMPATIBLE_MODEL:}          # z.B. qwen-max, mixtral-8x7b
```

---

## .env.example

```env
# LLM Provider wählen: anthropic | openai | ollama | openai-compatible
NEXO_LLM_PROVIDER=anthropic

# Anthropic (Claude)
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-4-5-20251001

# OpenAI
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o

# Ollama (lokal, kein API-Key)
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:7b

# OpenAI-Compatible (Qwen via DashScope, Groq, Together AI, etc.)
LLM_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_COMPATIBLE_API_KEY=sk-...
LLM_COMPATIBLE_MODEL=qwen-max
```

---

## Fix: AgentSessionService.java refactoren

```java
@Service
@Slf4j
public class AgentSessionService {

    private final LlmProviderFactory llmProviderFactory;
    private final OntologyAgentTools agentTools;
    private final AgentSessionRepository sessionRepository;

    public AgentResponse chat(UUID sessionId, UUID tenantId, String userMessage) {
        LlmProvider llm = llmProviderFactory.getProvider();

        // 1. Tools als JSON-Schema laden
        List<LlmToolDefinition> tools = agentTools.getAllToolDefinitions();

        // 2. Session-History laden
        List<LlmMessage> history = loadHistory(sessionId);
        history.add(LlmMessage.user(userMessage));

        // 3. System Prompt mit Ontology-Kontext
        String systemPrompt = buildSystemPrompt(tenantId);

        // 4. Agentic Loop: LLM aufrufen bis kein Tool-Call mehr
        LlmResponse response = llm.chatWithTools(systemPrompt, history, tools);
        List<ToolCallSummary> allToolCalls = new ArrayList<>();

        while (response.hasToolCalls()) {
            for (LlmResponse.LlmToolCall toolCall : response.getToolCalls()) {
                log.info("[Agent/{}] Tool: {} args: {}",
                    llm.getProviderName(), toolCall.name(), toolCall.arguments());

                // Tool ausführen
                String toolResult = agentTools.executeTool(toolCall.name(), toolCall.arguments(), tenantId);

                allToolCalls.add(new ToolCallSummary(toolCall.name(), toolCall.arguments(), toolResult));

                // Tool-Result zur History hinzufügen
                history.add(LlmMessage.toolResult(toolCall.id(), toolResult));
            }

            // Nächster LLM-Aufruf mit Tool-Results
            response = llm.chatWithTools(systemPrompt, history, tools);
        }

        // 5. History speichern
        history.add(LlmMessage.assistant(response.content()));
        saveHistory(sessionId, history);

        // 6. Audit Log
        agentAuditLogService.log(sessionId, tenantId, userMessage, response.content(), allToolCalls);

        log.info("[Agent/{}] Done. Input tokens: {}, Output tokens: {}, Tools used: {}",
            llm.getProviderName(), response.inputTokens(), response.outputTokens(), allToolCalls.size());

        return AgentResponse.builder()
            .message(response.content())
            .toolCalls(allToolCalls)
            .sessionId(sessionId)
            .providerUsed(llm.getProviderName())
            .build();
    }
}
```

---

## Docker Compose: Ollama (optional)

```yaml
# docker-compose.yml — optional für lokale Modelle
  ollama:
    image: ollama/ollama:latest
    container_name: nexo-ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    profiles:
      - ollama   # Nur starten mit: docker compose --profile ollama up
    # GPU Support (optional):
    # deploy:
    #   resources:
    #     reservations:
    #       devices:
    #         - driver: nvidia
    #           count: 1
    #           capabilities: [gpu]
```

```bash
# Qwen Modell herunterladen (einmalig)
docker exec nexo-ollama ollama pull qwen2.5:7b

# Oder grösseres Modell:
docker exec nexo-ollama ollama pull qwen2.5:32b
```

---

## Provider-Übersicht für Deployment

| Szenario | Provider | Config |
|---|---|---|
| NEXO AI Produktion | `anthropic` | `claude-sonnet-4-5-20251001` |
| Kunde will OpenAI | `openai` | `gpt-4o` |
| Kunde will Qwen (China) | `openai-compatible` | DashScope Endpoint |
| Air-Gapped / Offline | `ollama` | `qwen2.5:7b` lokal |
| Günstig / Speed | `openai-compatible` | Groq API (`llama-3.1-70b`) |
| Open Source only | `ollama` | `mistral:7b` oder `llama3.2` |

---

## Akzeptanzkriterien

- [ ] `NEXO_LLM_PROVIDER=anthropic` → Agent nutzt Claude, antwortet sinnvoll
- [ ] `NEXO_LLM_PROVIDER=openai` → Agent nutzt GPT-4o ohne Code-Änderung
- [ ] `NEXO_LLM_PROVIDER=ollama` + Ollama läuft → Agent nutzt lokales Modell
- [ ] `NEXO_LLM_PROVIDER=openai-compatible` mit Groq-Key → funktioniert
- [ ] Tool-Calling funktioniert bei allen Providern (searchObjects, traverseLinks, etc.)
- [ ] Ungültiger Provider in Config → klare Fehlermeldung beim Start
- [ ] `providerUsed` im AgentResponse zeigt welcher Provider aktiv war
- [ ] Agentic Loop bricht ab nach max. 10 Tool-Calls (Schutz vor Endlosschleifen)
