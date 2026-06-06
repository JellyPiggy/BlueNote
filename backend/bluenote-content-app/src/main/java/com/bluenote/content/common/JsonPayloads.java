package com.bluenote.content.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonPayloads {

    private final ObjectMapper objectMapper;

    public JsonPayloads(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String stringify(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event payload", exception);
        }
    }

    public <T> T parse(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse JSON payload", exception);
        }
    }
}

