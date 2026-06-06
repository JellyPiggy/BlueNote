package com.bluenote.content.note.api.dto;

public record NoteCountsResponse(
        Long likeCount,
        Long collectCount,
        Long commentCount
) {
}

