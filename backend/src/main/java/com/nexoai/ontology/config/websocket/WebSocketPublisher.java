package com.nexoai.ontology.config.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.cdc.ObjectChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketPublisher {

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public void registerSession(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket session registered: {}", session.getId());
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
        log.info("WebSocket session removed: {}", session.getId());
    }

    public void broadcastChange(ObjectChangeEvent event) {
        sessions.removeIf(s -> !s.isOpen());
        if (sessions.isEmpty()) return;

        try {
            String message = objectMapper.writeValueAsString(event);
            TextMessage wsMessage = new TextMessage(message);

            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(wsMessage);
                    }
                } catch (IOException e) {
                    log.warn("Failed to send WS message to {}: {}", session.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize change event: {}", e.getMessage());
        }
    }

    public void publishToTenant(UUID tenantId, ObjectChangeEvent event) {
        broadcastChange(event);
    }

    public int getActiveSessionCount() {
        sessions.removeIf(s -> !s.isOpen());
        return sessions.size();
    }
}
