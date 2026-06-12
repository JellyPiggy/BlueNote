package com.bluenote.content.note.api.dto;

import java.util.List;

public record AuthorRecentNotesResponse(
        List<AuthorRecentNotesItem> authors
) {
}
