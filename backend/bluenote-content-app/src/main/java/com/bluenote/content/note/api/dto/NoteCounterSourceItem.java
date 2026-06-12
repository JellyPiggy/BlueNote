package com.bluenote.content.note.api.dto;

import java.util.Map;

public record NoteCounterSourceItem(
        String targetType,
        String targetId,
        Map<String, Long> counts
) {
}
