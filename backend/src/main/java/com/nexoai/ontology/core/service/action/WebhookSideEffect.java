package com.nexoai.ontology.core.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexoai.ontology.core.domain.action.ActionType;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookSideEffect implements SideEffect {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getType() {
        return "WEBHOOK";
    }

    @Override
    @Async
    public void triggerAsync(ActionType actionType, OntologyObject object, JsonNode newState) {
        JsonNode config = actionType.getSideEffectConfig("WEBHOOK");
        if (config == null || !config.has("url")) {
            log.warn("Webhook side effect missing URL for action: {}", actionType.getApiName());
            return;
        }

        String url = config.get("url").asText();
        log.info("Triggering webhook for action {} to {}", actionType.getApiName(), url);

        webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(Map.of(
                        "actionType", actionType.getApiName(),
                        "objectId", object != null ? object.getId().toString() : "",
                        "newState", newState,
                        "timestamp", Instant.now().toString()
                ))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Webhook failed for action {}: {}", actionType.getApiName(), e.getMessage()))
                .subscribe();
    }
}
