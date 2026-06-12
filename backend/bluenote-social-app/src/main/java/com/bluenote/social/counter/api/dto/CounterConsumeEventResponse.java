package com.bluenote.social.counter.api.dto;

public record CounterConsumeEventResponse(
        String eventId,
        String consumeStatus,
        int deltaCount
) {
}
