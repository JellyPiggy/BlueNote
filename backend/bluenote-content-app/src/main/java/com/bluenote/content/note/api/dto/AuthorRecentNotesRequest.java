package com.bluenote.content.note.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AuthorRecentNotesRequest(
        @NotEmpty
        @Size(max = 50)
        List<String> authorIds,

        Integer limitPerAuthor,

        String publishedAfter
) {
}
