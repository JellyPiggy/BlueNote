package com.bluenote.common.mq;

import java.time.OffsetDateTime;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        String traceId,
        String producer,
        String bizKey,
        T payload
) {
}
