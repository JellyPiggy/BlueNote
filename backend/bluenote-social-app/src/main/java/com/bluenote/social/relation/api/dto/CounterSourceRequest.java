package com.bluenote.social.relation.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CounterSourceRequest(
        @NotEmpty
        @Size(max = 100)
        List<@Valid CounterSourceTarget> targets
) {
}
