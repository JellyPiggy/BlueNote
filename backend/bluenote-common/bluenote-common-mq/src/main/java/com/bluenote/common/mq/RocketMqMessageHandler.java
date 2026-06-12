package com.bluenote.common.mq;

import java.util.List;

public interface RocketMqMessageHandler {

    String consumerGroup();

    List<String> topics();

    void handle(MqInboundMessage message);
}
