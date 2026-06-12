package com.bluenote.social.relation.api.dto;

import java.util.Map;

public record CounterSourceItem(
        String targetType,
        String targetId,
        Map<String, Long> counts
) {
}
