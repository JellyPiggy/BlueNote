package com.bluenote.common.mq;

public record OutboxRetryRequest(
        String tableName,
        String eventId
) {
}
