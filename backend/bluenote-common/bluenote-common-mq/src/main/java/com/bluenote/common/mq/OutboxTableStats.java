package com.bluenote.common.mq;

public record OutboxTableStats(
        String tableName,
        long initCount,
        long retryableFailedCount,
        long deadLetterCount,
        long sentCount
) {
}
