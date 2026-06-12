package com.bluenote.social.counter.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CounterBatchRequest(
        @Valid
        @NotEmpty
        @Size(max = 100)
        List<CounterTarget> targets
) {
}
