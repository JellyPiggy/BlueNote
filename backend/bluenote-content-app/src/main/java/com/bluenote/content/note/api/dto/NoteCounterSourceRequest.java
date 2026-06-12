package com.bluenote.content.note.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NoteCounterSourceRequest(
        @Valid
        @NotEmpty
        @Size(max = 100)
        List<NoteCounterSourceTarget> targets
) {
}
