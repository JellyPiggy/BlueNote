package com.bluenote.content.note.api.dto;

public record ViewerActionResponse(
        boolean liked,
        boolean collected
) {
}

