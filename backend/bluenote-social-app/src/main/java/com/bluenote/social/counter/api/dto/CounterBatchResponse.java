package com.bluenote.social.counter.api.dto;

import java.util.List;

public record CounterBatchResponse(List<CounterItem> items) {
}
