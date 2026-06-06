package com.bluenote.content.note.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchNoteSummaryRequest(
        @NotEmpty List<String> noteIds,
        String viewerId,
        Boolean includeInvisible
) {
}

