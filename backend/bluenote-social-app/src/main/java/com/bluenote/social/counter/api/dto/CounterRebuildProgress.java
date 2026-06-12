package com.bluenote.social.counter.api.dto;

public record CounterRebuildProgress(
        int total,
        int success,
        int failed
) {
}
