package com.bluenote.social.push.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluenote.social.push.application.PushRealtimeAckService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RealtimeSessionRegistry sessionRegistry;
    private final PushRealtimeAckService ackService;
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(
            RealtimeSessionRegistry sessionRegistry,
            PushRealtimeAckService ackService,
            ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.ackService = ackService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = attribute(session, RealtimeHandshakeInterceptor.ATTR_USER_ID, Long.class);
        String deviceId = attribute(session, RealtimeHandshakeInterceptor.ATTR_DEVICE_ID, String.class);
        String sessionId = attribute(session, RealtimeHandshakeInterceptor.ATTR_SESSION_ID, String.class);
        if (userId == null || deviceId == null || sessionId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("MISSING_CONTEXT"));
            return;
        }
        sessionRegistry.register(session.getId(), userId, deviceId, sessionId, session);
        send(session, Map.of(
                "type", "CONNECTED",
                "connectionId", session.getId(),
                "deviceId", deviceId,
                "serverTime", nowString()
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = parse(message.getPayload());
        String type = String.valueOf(payload.getOrDefault("type", "")).toUpperCase();
        if ("PING".equals(type)) {
            sessionRegistry.touch(session.getId());
            send(session, Map.of("type", "PONG", "serverTime", nowString()));
            return;
        }
        if ("ACK".equals(type)) {
            sessionRegistry.touch(session.getId());
            String deviceId = attribute(session, RealtimeHandshakeInterceptor.ATTR_DEVICE_ID, String.class);
            ackService.ack(stringValue(payload.get("requestId")), deviceId);
            send(session, Map.of(
                    "type", "ACK_RECEIVED",
                    "requestId", payload.get("requestId"),
                    "serverTime", nowString()
            ));
            return;
        }
        send(session, Map.of("type", "ERROR", "reason", "UNSUPPORTED_MESSAGE_TYPE", "serverTime", nowString()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Realtime websocket transport error sessionId={} message={}", session.getId(), exception.getMessage());
        sessionRegistry.remove(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session.getId());
    }

    @SuppressWarnings("unchecked")
    private <T> T attribute(WebSocketSession session, String name, Class<T> type) {
        Object value = session.getAttributes().get(name);
        return type.isInstance(value) ? (T) value : null;
    }

    private Map<String, Object> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>(payload);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(body)));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nowString() {
        return LocalDateTime.now(ZONE).atZone(ZONE).toOffsetDateTime().toString();
    }
}
