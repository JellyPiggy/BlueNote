package com.bluenote.content.note.api.dto;

import java.util.List;

public record BatchNoteSummaryResponse(
        List<NoteSummaryItem> notes
) {
}

