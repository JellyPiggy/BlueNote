package com.bluenote.content.note.api.dto;

public record NoteAuthorResponse(
        String userId,
        String nickname,
        String avatarUrl,
        String userStatus
) {
}

