package com.bluenote.order.infrastructure.mq;

import com.bluenote.order.config.OrderConsumerExecutorProperties;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumerExecutor implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerExecutor.class);

    private final Map<PoolKind, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();
    private final Map<PoolKind, Duration> awaitTimeouts = new ConcurrentHashMap<>();

    public OrderConsumerExecutor(OrderConsumerExecutorProperties properties) {
        register(PoolKind.SECKILL, properties.getSeckill());
        register(PoolKind.TIMEOUT, properties.getTimeout());
    }

    public void execute(PoolKind kind, Runnable task) {
        ThreadPoolExecutor executor = executors.get(kind);
        Duration awaitTimeout = awaitTimeouts.get(kind);
        if (executor == null || awaitTimeout == null) {
            throw new IllegalArgumentException("Unsupported order consumer executor kind: " + kind);
        }
        Future<?> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException exception) {
            throw new OrderConsumerExecutorException("Order consumer executor saturated, kind=" + kind, exception);
        }
        try {
            future.get(awaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OrderConsumerExecutorException("Order consumer task interrupted, kind=" + kind, exception);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new OrderConsumerExecutorException("Order consumer task timeout, kind=" + kind, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new OrderConsumerExecutorException("Order consumer task failed, kind=" + kind, cause);
        }
    }

    @Override
    public void destroy() {
        for (Map.Entry<PoolKind, ThreadPoolExecutor> entry : executors.entrySet()) {
            entry.getValue().shutdown();
            log.info("Order consumer executor stopped, kind={}", entry.getKey());
        }
    }

    private void register(PoolKind kind, OrderConsumerExecutorProperties.Pool properties) {
        int coreSize = Math.max(1, properties.getCoreSize());
        int maxSize = Math.max(coreSize, properties.getMaxSize());
        int queueCapacity = Math.max(0, properties.getQueueCapacity());
        int keepAliveSeconds = Math.max(1, properties.getKeepAliveSeconds());
        int awaitTimeoutSeconds = Math.max(1, properties.getAwaitTimeoutSeconds());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                queue(queueCapacity),
                new NamedThreadFactory("order-" + kind.metricTag() + "-consumer-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(false);
        executors.put(kind, executor);
        awaitTimeouts.put(kind, Duration.ofSeconds(awaitTimeoutSeconds));
        log.info(
                "Order consumer executor started, kind={}, coreSize={}, maxSize={}, queueCapacity={}, awaitTimeoutSeconds={}",
                kind, coreSize, maxSize, queueCapacity, awaitTimeoutSeconds
        );
    }

    private BlockingQueue<Runnable> queue(int queueCapacity) {
        if (queueCapacity == 0) {
            return new SynchronousQueue<>();
        }
        return new ArrayBlockingQueue<>(queueCapacity);
    }

    public enum PoolKind {
        SECKILL("seckill"),
        TIMEOUT("timeout");

        private final String metricTag;

        PoolKind(String metricTag) {
            this.metricTag = metricTag;
        }

        private String metricTag() {
            return metricTag;
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger index = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + index.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }

    public static class OrderConsumerExecutorException extends RuntimeException {

        public OrderConsumerExecutorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
