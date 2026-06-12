package com.bluenote.common.mq;

public record OutboxRetryResponse(
        String tableName,
        String eventId,
        boolean retried
) {
}
