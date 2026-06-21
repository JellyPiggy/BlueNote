package com.bluenote.content.comment.infrastructure.mq;

import com.bluenote.common.mq.MqInboundMessage;
import com.bluenote.common.mq.RocketMqMessageHandler;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.comment.api.dto.CommentConsumeEventRequest;
import com.bluenote.content.comment.application.CommentApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommentHotMqMessageHandler implements RocketMqMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(CommentHotMqMessageHandler.class);
    private static final String CONSUMER_GROUP = "bluenote-comment-hot-consumer";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final CommentApplicationService commentApplicationService;
    private final ObjectMapper objectMapper;

    public CommentHotMqMessageHandler(
            CommentApplicationService commentApplicationService,
            ObjectMapper objectMapper
    ) {
        this.commentApplicationService = commentApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerGroup() {
        return CONSUMER_GROUP;
    }

    @Override
    public List<String> topics() {
        return List.of("comment-event");
    }

    @Override
    public void handle(MqInboundMessage message) {
        CommentConsumeEventRequest request = toRequest(message);
        String previousTraceId = TraceIdHolder.current();
        if (request.traceId() != null && !request.traceId().isBlank()) {
            TraceIdHolder.set(request.traceId());
        }
        try {
            commentApplicationService.consumeEvent(request);
            log.info("Comment hot cache MQ event consumed, group={}, topic={}, eventId={}, eventType={}",
                    CONSUMER_GROUP, message.topic(), request.eventId(), request.eventType());
        } finally {
            if (previousTraceId == null || previousTraceId.isBlank()) {
                TraceIdHolder.clear();
            } else {
                TraceIdHolder.set(previousTraceId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private CommentConsumeEventRequest toRequest(MqInboundMessage message) {
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(message.body(), MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid MQ event envelope JSON", exception);
        }
        Object payload = envelope.get("payload");
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            throw new IllegalArgumentException("MQ event envelope payload must be object");
        }
        return new CommentConsumeEventRequest(
                message.topic(),
                CONSUMER_GROUP,
                requiredString(envelope, "eventId"),
                requiredString(envelope, "eventType"),
                intValue(envelope.get("eventVersion")),
                requiredString(envelope, "occurredAt"),
                stringValue(envelope.get("traceId")),
                stringValue(envelope.get("producer")),
                stringValue(envelope.get("bizKey")),
                (Map<String, Object>) payloadMap
        );
    }

    private String requiredString(Map<String, Object> envelope, String field) {
        String value = stringValue(envelope.get(field));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MQ event envelope missing " + field);
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
