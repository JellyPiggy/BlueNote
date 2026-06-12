package com.bluenote.common.mq;

import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(DefaultMQProducer.class)
public class RocketMqProducerClient implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqProducerClient.class);

    private final BluenoteMqProperties properties;
    private DefaultMQProducer producer;

    public RocketMqProducerClient(BluenoteMqProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!properties.isEnabled()) {
            log.info("RocketMQ producer is disabled");
            return;
        }
        String group = properties.getProducerGroup();
        if (group == null || group.isBlank()) {
            group = "bluenote-outbox-producer";
        }
        producer = new DefaultMQProducer(group);
        producer.setNamesrvAddr(properties.getNameServer());
        producer.setSendMsgTimeout(properties.getSendTimeoutMillis());
        producer.start();
        log.info("RocketMQ producer started, group={}, nameServer={}", group, properties.getNameServer());
    }

    public boolean available() {
        return properties.isEnabled() && producer != null;
    }

    public SendResult send(String topic, String key, String body) throws Exception {
        if (!available()) {
            throw new IllegalStateException("RocketMQ producer is not available");
        }
        Message message = new Message(topic, body.getBytes(StandardCharsets.UTF_8));
        if (key != null && !key.isBlank()) {
            message.setKeys(key);
        }
        return producer.send(message);
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
            log.info("RocketMQ producer stopped");
        }
    }
}
