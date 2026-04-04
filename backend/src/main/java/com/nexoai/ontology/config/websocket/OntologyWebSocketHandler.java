package com.nexoai.ontology.config.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class OntologyWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketPublisher publisher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        publisher.registerSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        publisher.removeSession(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client can send subscription filters here (future)
        log.debug("WS message from {}: {}", session.getId(), message.getPayload());
    }
}
