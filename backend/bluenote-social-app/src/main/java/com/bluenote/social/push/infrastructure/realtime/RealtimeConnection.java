package com.bluenote.social.push.infrastructure.realtime;

import java.time.LocalDateTime;
import org.springframework.web.socket.WebSocketSession;

public record RealtimeConnection(
        String connectionId,
        Long userId,
        String deviceId,
        String sessionId,
        WebSocketSession session,
        LocalDateTime connectedAt,
        LocalDateTime lastSeenAt
) {
    public RealtimeConnection touch(LocalDateTime now) {
        return new RealtimeConnection(connectionId, userId, deviceId, sessionId, session, connectedAt, now);
    }
}
