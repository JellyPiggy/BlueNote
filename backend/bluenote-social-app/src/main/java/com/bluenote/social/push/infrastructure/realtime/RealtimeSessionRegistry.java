package com.bluenote.social.push.infrastructure.realtime;

import com.bluenote.social.push.api.dto.PushOnlineDeviceItem;
import com.bluenote.social.push.api.dto.PushOnlineStateResponse;
import com.bluenote.social.push.infrastructure.redis.PushRedisStore;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RealtimeSessionRegistry {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final Map<String, RealtimeConnection> byConnectionId = new ConcurrentHashMap<>();
    private final Map<String, String> connectionIdByDeviceId = new ConcurrentHashMap<>();
    private final PushRedisStore redisStore;
    private final String nodeId;

    public RealtimeSessionRegistry(
            PushRedisStore redisStore,
            @Value("${bluenote.push.realtime.node-id:${spring.application.name:bluenote-social-app}}") String nodeId
    ) {
        this.redisStore = redisStore;
        this.nodeId = nodeId;
    }

    public void register(String connectionId, Long userId, String deviceId, String sessionId, WebSocketSession session) {
        LocalDateTime now = now();
        String previousConnectionId = connectionIdByDeviceId.put(deviceId, connectionId);
        if (previousConnectionId != null && !previousConnectionId.equals(connectionId)) {
            close(previousConnectionId, CloseStatus.POLICY_VIOLATION.withReason("REPLACED"));
        }
        RealtimeConnection connection = new RealtimeConnection(connectionId, userId, deviceId, sessionId, session, now, now);
        byConnectionId.put(connectionId, connection);
        redisStore.markDeviceOnline(userId, deviceId, connectionId, nodeId, now);
    }

    public Optional<RealtimeConnection> find(String connectionId) {
        return Optional.ofNullable(byConnectionId.get(connectionId));
    }

    public List<RealtimeConnection> onlineConnections(Long userId) {
        return byConnectionId.values().stream()
                .filter(connection -> connection.userId().equals(userId))
                .filter(connection -> connection.session().isOpen())
                .sorted(Comparator.comparing(RealtimeConnection::lastSeenAt).reversed())
                .toList();
    }

    public boolean isOnline(Long userId, String deviceId) {
        String connectionId = connectionIdByDeviceId.get(deviceId);
        if (connectionId == null) {
            return false;
        }
        RealtimeConnection connection = byConnectionId.get(connectionId);
        return connection != null && connection.userId().equals(userId) && connection.session().isOpen();
    }

    public void touch(String connectionId) {
        RealtimeConnection existing = byConnectionId.get(connectionId);
        if (existing == null) {
            return;
        }
        RealtimeConnection touched = existing.touch(now());
        byConnectionId.put(connectionId, touched);
        redisStore.touchDeviceOnline(touched.userId(), touched.deviceId(), touched.lastSeenAt());
    }

    public void remove(String connectionId) {
        RealtimeConnection removed = byConnectionId.remove(connectionId);
        if (removed == null) {
            return;
        }
        connectionIdByDeviceId.remove(removed.deviceId(), connectionId);
        redisStore.removeDeviceOnline(removed.userId(), removed.deviceId());
    }

    public boolean kick(Long userId, String deviceId, String reason) {
        String connectionId = connectionIdByDeviceId.get(deviceId);
        if (connectionId == null) {
            return false;
        }
        RealtimeConnection connection = byConnectionId.get(connectionId);
        if (connection == null || !connection.userId().equals(userId)) {
            return false;
        }
        close(connectionId, CloseStatus.NORMAL.withReason(reason == null || reason.isBlank() ? "KICKED" : reason));
        remove(connectionId);
        return true;
    }

    public PushOnlineStateResponse onlineState(Long userId) {
        List<PushOnlineDeviceItem> devices = onlineConnections(userId).stream()
                .map(connection -> new PushOnlineDeviceItem(
                        connection.deviceId(),
                        connection.connectionId(),
                        toOffsetString(connection.connectedAt()),
                        toOffsetString(connection.lastSeenAt())
                ))
                .toList();
        return new PushOnlineStateResponse(String.valueOf(userId), !devices.isEmpty(), devices);
    }

    private void close(String connectionId, CloseStatus status) {
        RealtimeConnection connection = byConnectionId.get(connectionId);
        if (connection == null || !connection.session().isOpen()) {
            return;
        }
        try {
            connection.session().close(status);
        } catch (Exception ignored) {
            // Session cleanup is best effort; removal happens through the registry.
        }
    }

    private String toOffsetString(LocalDateTime time) {
        return time.atZone(ZONE).toOffsetDateTime().toString();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
