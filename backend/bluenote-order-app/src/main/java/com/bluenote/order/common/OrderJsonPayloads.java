package com.bluenote.order.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OrderJsonPayloads {

    private final ObjectMapper objectMapper;

    public OrderJsonPayloads(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String stringify(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize order payload", exception);
        }
    }
}
