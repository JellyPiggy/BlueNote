package com.bluenote.social.push.infrastructure.realtime;

import com.bluenote.social.push.infrastructure.entity.PushDeliveryRequestEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

@Component
public class RealtimeMessageSender {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final RealtimeSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public RealtimeMessageSender(RealtimeSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    public RealtimeDeliveryResult send(PushDeliveryRequestEntity request, String deviceId, Map<String, Object> data) {
        return sessionRegistry.onlineConnections(request.getTargetUserId()).stream()
                .filter(connection -> connection.deviceId().equals(deviceId))
                .findFirst()
                .map(connection -> {
                    try {
                        connection.session().sendMessage(new TextMessage(message(request, data)));
                        sessionRegistry.touch(connection.connectionId());
                        return RealtimeDeliveryResult.delivered(connection.connectionId());
                    } catch (IOException | IllegalStateException exception) {
                        return RealtimeDeliveryResult.failed(exception.getMessage());
                    }
                })
                .orElseGet(() -> RealtimeDeliveryResult.failed("DEVICE_OFFLINE"));
    }

    public boolean isOnline(Long userId, String deviceId) {
        return sessionRegistry.isOnline(userId, deviceId);
    }

    private String message(PushDeliveryRequestEntity request, Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "PUSH_MESSAGE");
        body.put("requestId", request.getRequestId());
        body.put("scene", request.getScene());
        body.put("title", request.getTitle());
        body.put("body", request.getBody());
        body.put("data", data == null ? Map.of() : data);
        body.put("sentAt", LocalDateTime.now(ZONE).atZone(ZONE).toOffsetDateTime().toString());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize realtime message", exception);
        }
    }
}
