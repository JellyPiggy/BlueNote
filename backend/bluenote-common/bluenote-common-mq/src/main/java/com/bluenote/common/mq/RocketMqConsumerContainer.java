package com.bluenote.common.mq;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(DefaultMQPushConsumer.class)
public class RocketMqConsumerContainer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqConsumerContainer.class);

    private final BluenoteMqProperties properties;
    private final List<RocketMqMessageHandler> handlers;
    private final List<DefaultMQPushConsumer> consumers = new ArrayList<>();

    public RocketMqConsumerContainer(BluenoteMqProperties properties, List<RocketMqMessageHandler> handlers) {
        this.properties = properties;
        this.handlers = handlers;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!properties.isEnabled() || handlers.isEmpty()) {
            return;
        }
        for (RocketMqMessageHandler handler : handlers) {
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(handler.consumerGroup());
            consumer.setNamesrvAddr(properties.getNameServer());
            consumer.setConsumeThreadMin(properties.getConsumer().getConsumeThreadMin());
            consumer.setConsumeThreadMax(properties.getConsumer().getConsumeThreadMax());
            consumer.setMaxReconsumeTimes(properties.getConsumer().getMaxReconsumeTimes());
            for (String topic : handler.topics()) {
                consumer.subscribe(topic, "*");
            }
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) ->
                    consume(handler, messages, context)
            );
            consumer.start();
            consumers.add(consumer);
            log.info("RocketMQ consumer started, group={}, topics={}", handler.consumerGroup(), handler.topics());
        }
    }

    private ConsumeConcurrentlyStatus consume(
            RocketMqMessageHandler handler,
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context
    ) {
        try {
            for (MessageExt message : messages) {
                handler.handle(new MqInboundMessage(
                        message.getTopic(),
                        message.getMsgId(),
                        message.getKeys(),
                        new String(message.getBody(), StandardCharsets.UTF_8)
                ));
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException exception) {
            log.warn("RocketMQ consume failed, group={}, delay retry", handler.consumerGroup(), exception);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    @Override
    public void destroy() {
        for (DefaultMQPushConsumer consumer : consumers) {
            consumer.shutdown();
        }
        if (!consumers.isEmpty()) {
            log.info("RocketMQ consumers stopped, count={}", consumers.size());
        }
    }
}
