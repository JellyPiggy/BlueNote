package com.bluenote.social.counter.api.dto;

import java.util.Map;

public record CounterItem(
        String targetType,
        String targetId,
        Map<String, Long> counts,
        boolean degraded
) {
}
