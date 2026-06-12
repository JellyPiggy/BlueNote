package com.bluenote.content.note.api.dto;

import java.util.List;

public record AuthorRecentNotesItem(
        String authorId,
        List<NoteSummaryItem> notes
) {
}
