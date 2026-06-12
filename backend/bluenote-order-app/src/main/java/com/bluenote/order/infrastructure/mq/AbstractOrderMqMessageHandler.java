package com.bluenote.order.infrastructure.mq;

import com.bluenote.common.mq.MqInboundMessage;
import com.bluenote.common.mq.RocketMqMessageHandler;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.order.api.dto.OrderDtos.OrderConsumeEventRequest;
import com.bluenote.order.application.OrderApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractOrderMqMessageHandler implements RocketMqMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractOrderMqMessageHandler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String consumerGroup;
    private final List<String> topics;
    private final OrderApplicationService orderApplicationService;
    private final ObjectMapper objectMapper;

    AbstractOrderMqMessageHandler(
            String consumerGroup,
            List<String> topics,
            OrderApplicationService orderApplicationService,
            ObjectMapper objectMapper
    ) {
        this.consumerGroup = consumerGroup;
        this.topics = topics;
        this.orderApplicationService = orderApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerGroup() {
        return consumerGroup;
    }

    @Override
    public List<String> topics() {
        return topics;
    }

    @Override
    public void handle(MqInboundMessage message) {
        OrderConsumeEventRequest request = toRequest(message);
        String previousTraceId = TraceIdHolder.current();
        if (request.traceId() != null && !request.traceId().isBlank()) {
            TraceIdHolder.set(request.traceId());
        }
        try {
            orderApplicationService.consumeEvent(request);
            log.info("Order MQ event consumed, group={}, topic={}, eventId={}, eventType={}",
                    consumerGroup, message.topic(), request.eventId(), request.eventType());
        } finally {
            if (previousTraceId == null || previousTraceId.isBlank()) {
                TraceIdHolder.clear();
            } else {
                TraceIdHolder.set(previousTraceId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private OrderConsumeEventRequest toRequest(MqInboundMessage message) {
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
        return new OrderConsumeEventRequest(
                message.topic(),
                consumerGroup,
                requiredString(envelope, "eventId"),
                requiredString(envelope, "eventType"),
                intValue(envelope.get("eventVersion")),
                requiredString(envelope, "occurredAt"),
                stringValue(envelope.get("traceId")),
                stringValue(envelope.get("producer")),
                stringValue(envelope.get("bizKey")),
                (Map<String, Object>) payloadMap,
                message.body()
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
