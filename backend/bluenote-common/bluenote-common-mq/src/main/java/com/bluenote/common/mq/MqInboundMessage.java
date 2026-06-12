package com.bluenote.common.mq;

public record MqInboundMessage(
        String topic,
        String messageId,
        String keys,
        String body
) {
}
