package com.bluenote.common.mq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluenote.mq")
public class BluenoteMqProperties {

    private boolean enabled = false;
    private String nameServer = "127.0.0.1:9876";
    private String producerGroup;
    private int sendTimeoutMillis = 3000;
    private final Consumer consumer = new Consumer();
    private final Outbox outbox = new Outbox();
    private final Map<String, String> topicMappings = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public int getSendTimeoutMillis() {
        return sendTimeoutMillis;
    }

    public void setSendTimeoutMillis(int sendTimeoutMillis) {
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Map<String, String> getTopicMappings() {
        return topicMappings;
    }

    public static class Consumer {

        private int maxReconsumeTimes = 16;
        private int consumeThreadMin = 1;
        private int consumeThreadMax = 4;

        public int getMaxReconsumeTimes() {
            return maxReconsumeTimes;
        }

        public void setMaxReconsumeTimes(int maxReconsumeTimes) {
            this.maxReconsumeTimes = maxReconsumeTimes;
        }

        public int getConsumeThreadMin() {
            return consumeThreadMin;
        }

        public void setConsumeThreadMin(int consumeThreadMin) {
            this.consumeThreadMin = consumeThreadMin;
        }

        public int getConsumeThreadMax() {
            return consumeThreadMax;
        }

        public void setConsumeThreadMax(int consumeThreadMax) {
            this.consumeThreadMax = consumeThreadMax;
        }
    }

    public static class Outbox {

        private boolean enabled = false;
        private int batchSize = 50;
        private int maxRetry = 12;
        private long initialDelayMillis = 5000;
        private long fixedDelayMillis = 2000;
        private long retryBaseDelaySeconds = 5;
        private long retryMaxDelaySeconds = 300;
        private final List<OutboxTable> tables = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }

        public long getInitialDelayMillis() {
            return initialDelayMillis;
        }

        public void setInitialDelayMillis(long initialDelayMillis) {
            this.initialDelayMillis = initialDelayMillis;
        }

        public long getFixedDelayMillis() {
            return fixedDelayMillis;
        }

        public void setFixedDelayMillis(long fixedDelayMillis) {
            this.fixedDelayMillis = fixedDelayMillis;
        }

        public long getRetryBaseDelaySeconds() {
            return retryBaseDelaySeconds;
        }

        public void setRetryBaseDelaySeconds(long retryBaseDelaySeconds) {
            this.retryBaseDelaySeconds = retryBaseDelaySeconds;
        }

        public long getRetryMaxDelaySeconds() {
            return retryMaxDelaySeconds;
        }

        public void setRetryMaxDelaySeconds(long retryMaxDelaySeconds) {
            this.retryMaxDelaySeconds = retryMaxDelaySeconds;
        }

        public List<OutboxTable> getTables() {
            return tables;
        }
    }

    public static class OutboxTable {

        private boolean enabled = true;
        private String tableName;
        private String statusColumn = "send_status";
        private String eventIdColumn = "event_id";
        private String eventTypeColumn = "event_type";
        private String aggregateIdColumn = "aggregate_id";
        private String payloadColumn = "payload";
        private String retryCountColumn = "retry_count";
        private String nextRetryAtColumn = "next_retry_at";
        private String createdAtColumn = "created_at";
        private String updatedAtColumn = "updated_at";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getStatusColumn() {
            return statusColumn;
        }

        public void setStatusColumn(String statusColumn) {
            this.statusColumn = statusColumn;
        }

        public String getEventIdColumn() {
            return eventIdColumn;
        }

        public void setEventIdColumn(String eventIdColumn) {
            this.eventIdColumn = eventIdColumn;
        }

        public String getEventTypeColumn() {
            return eventTypeColumn;
        }

        public void setEventTypeColumn(String eventTypeColumn) {
            this.eventTypeColumn = eventTypeColumn;
        }

        public String getAggregateIdColumn() {
            return aggregateIdColumn;
        }

        public void setAggregateIdColumn(String aggregateIdColumn) {
            this.aggregateIdColumn = aggregateIdColumn;
        }

        public String getPayloadColumn() {
            return payloadColumn;
        }

        public void setPayloadColumn(String payloadColumn) {
            this.payloadColumn = payloadColumn;
        }

        public String getRetryCountColumn() {
            return retryCountColumn;
        }

        public void setRetryCountColumn(String retryCountColumn) {
            this.retryCountColumn = retryCountColumn;
        }

        public String getNextRetryAtColumn() {
            return nextRetryAtColumn;
        }

        public void setNextRetryAtColumn(String nextRetryAtColumn) {
            this.nextRetryAtColumn = nextRetryAtColumn;
        }

        public String getCreatedAtColumn() {
            return createdAtColumn;
        }

        public void setCreatedAtColumn(String createdAtColumn) {
            this.createdAtColumn = createdAtColumn;
        }

        public String getUpdatedAtColumn() {
            return updatedAtColumn;
        }

        public void setUpdatedAtColumn(String updatedAtColumn) {
            this.updatedAtColumn = updatedAtColumn;
        }
    }
}
