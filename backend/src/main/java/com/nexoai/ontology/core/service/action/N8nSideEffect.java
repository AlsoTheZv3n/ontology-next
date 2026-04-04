package com.nexoai.ontology.core.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexoai.ontology.core.domain.action.ActionType;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class N8nSideEffect implements SideEffect {

    private final WebClient.Builder webClientBuilder;

    @Value("${nexo.n8n.callback-base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    @Override
    public String getType() { return "N8N"; }

    @Override
    @Async
    public void triggerAsync(ActionType actionType, OntologyObject object, JsonNode newState) {
        JsonNode config = actionType.getSideEffectConfig("N8N");
        if (config == null || !config.has("webhookUrl")) {
            log.warn("N8N side effect missing webhookUrl for action: {}", actionType.getApiName());
            return;
        }

        String webhookUrl = config.get("webhookUrl").asText();
        String objectId = object != null ? object.getId().toString() : "";

        Map<String, Object> payload = Map.of(
                "actionType", actionType.getApiName(),
                "objectId", objectId,
                "objectType", object != null ? object.getObjectTypeName() : "",
                "triggerSource", "nexo-ontology",
                "timestamp", Instant.now().toString(),
                "data", newState,
                "callbackUrl", callbackBaseUrl + "/api/v1/inbound/n8n/callback/" + objectId
        );

        log.info("Triggering n8n workflow at {} for action {} / object {}",
                webhookUrl, actionType.getApiName(), objectId);

        webClientBuilder.build()
                .post()
                .uri(webhookUrl)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("n8n webhook delivered successfully"))
                .doOnError(e -> log.error("n8n webhook failed for action {}: {}",
                        actionType.getApiName(), e.getMessage()))
                .subscribe();
    }
}
