package com.bluenote.social.push.application;

import com.bluenote.social.push.infrastructure.mapper.PushDeliveryAttemptMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushRealtimeAckService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PushDeliveryAttemptMapper attemptMapper;

    public PushRealtimeAckService(PushDeliveryAttemptMapper attemptMapper) {
        this.attemptMapper = attemptMapper;
    }

    @Transactional
    public void ack(String requestId, String deviceId) {
        if (requestId == null || requestId.isBlank() || deviceId == null || deviceId.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        attemptMapper.markAcked(requestId, deviceId, now, now);
    }
}
