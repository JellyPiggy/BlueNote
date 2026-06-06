package com.bluenote.content.note.api.dto;

public record NoteSummaryItem(
        String noteId,
        String authorId,
        String title,
        String contentPreview,
        String coverFileId,
        String coverUrl,
        String noteStatus,
        String visibility,
        String publishedAt
) {
}

