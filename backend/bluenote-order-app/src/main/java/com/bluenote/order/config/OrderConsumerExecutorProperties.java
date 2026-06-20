package com.bluenote.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluenote.order.consumer-executor")
public class OrderConsumerExecutorProperties {

    private final Pool seckill = new Pool();
    private final Pool timeout = new Pool();

    public Pool getSeckill() {
        return seckill;
    }

    public Pool getTimeout() {
        return timeout;
    }

    public static class Pool {

        private int coreSize = 4;
        private int maxSize = 4;
        private int queueCapacity = 100;
        private int keepAliveSeconds = 60;
        private int awaitTimeoutSeconds = 60;

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public int getAwaitTimeoutSeconds() {
            return awaitTimeoutSeconds;
        }

        public void setAwaitTimeoutSeconds(int awaitTimeoutSeconds) {
            this.awaitTimeoutSeconds = awaitTimeoutSeconds;
        }
    }
}
