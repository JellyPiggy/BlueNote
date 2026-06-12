package com.bluenote.social.counter.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CounterWarmupTarget(
        @NotBlank
        String targetType,

        @NotBlank
        String targetId
) {
}
