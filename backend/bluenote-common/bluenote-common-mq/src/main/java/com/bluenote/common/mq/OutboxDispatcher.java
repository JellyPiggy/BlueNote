package com.bluenote.common.mq;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.rocketmq.client.producer.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_\\.]+");

    private final BluenoteMqProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final RocketMqProducerClient producerClient;

    public OutboxDispatcher(
            BluenoteMqProperties properties,
            JdbcTemplate jdbcTemplate,
            RocketMqProducerClient producerClient
    ) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.producerClient = producerClient;
    }

    @Scheduled(
            initialDelayString = "${bluenote.mq.outbox.initial-delay-millis:5000}",
            fixedDelayString = "${bluenote.mq.outbox.fixed-delay-millis:2000}"
    )
    public void dispatchScheduled() {
        if (!properties.isEnabled() || !properties.getOutbox().isEnabled() || !producerClient.available()) {
            return;
        }
        dispatchOnce();
    }

    public int dispatchOnce() {
        int dispatched = 0;
        for (BluenoteMqProperties.OutboxTable table : properties.getOutbox().getTables()) {
            if (!table.isEnabled()) {
                continue;
            }
            validate(table);
            dispatched += dispatchTable(table);
        }
        return dispatched;
    }

    public List<OutboxTableStats> stats() {
        List<OutboxTableStats> stats = new ArrayList<>();
        for (BluenoteMqProperties.OutboxTable table : properties.getOutbox().getTables()) {
            if (!table.isEnabled()) {
                continue;
            }
            validate(table);
            stats.add(tableStats(table));
        }
        return stats;
    }

    public boolean retry(String tableName, String eventId) {
        BluenoteMqProperties.OutboxTable table = findTable(tableName)
                .orElseThrow(() -> new IllegalArgumentException("Outbox table is not configured: " + tableName));
        validate(table);
        int updated = jdbcTemplate.update(
                "UPDATE " + tableName(table.getTableName()) + " SET "
                        + column(table.getStatusColumn()) + " = 'FAILED', "
                        + column(table.getRetryCountColumn()) + " = 0, "
                        + column(table.getNextRetryAtColumn()) + " = NULL, "
                        + column(table.getUpdatedAtColumn()) + " = NOW(3) "
                        + "WHERE " + column(table.getEventIdColumn()) + " = ? "
                        + "AND " + column(table.getStatusColumn()) + " = 'FAILED'",
                eventId
        );
        return updated > 0;
    }

    private int dispatchTable(BluenoteMqProperties.OutboxTable table) {
        List<OutboxEventRow> rows = jdbcTemplate.query(selectSql(table), (rs, rowNum) -> row(table, rs));
        int sent = 0;
        for (OutboxEventRow row : rows) {
            String topic = EventTopicResolver.resolve(row.eventType(), properties.getTopicMappings());
            if (topic == null || topic.isBlank()) {
                markFailure(table, row, new IllegalArgumentException("No topic mapping for " + row.eventType()));
                continue;
            }
            try {
                SendResult result = producerClient.send(topic, row.eventId(), row.payload());
                markSent(table, row);
                sent++;
                log.info("Outbox event sent, table={}, eventId={}, eventType={}, topic={}, messageId={}",
                        table.getTableName(), row.eventId(), row.eventType(), topic, result.getMsgId());
            } catch (Exception exception) {
                markFailure(table, row, exception);
                log.warn("Outbox event send failed, table={}, eventId={}, eventType={}",
                        table.getTableName(), row.eventId(), row.eventType(), exception);
            }
        }
        return sent;
    }

    private Optional<BluenoteMqProperties.OutboxTable> findTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return Optional.empty();
        }
        return properties.getOutbox().getTables().stream()
                .filter(BluenoteMqProperties.OutboxTable::isEnabled)
                .filter(table -> tableName.equals(table.getTableName()))
                .findFirst();
    }

    private OutboxTableStats tableStats(BluenoteMqProperties.OutboxTable table) {
        return jdbcTemplate.queryForObject(statsSql(table), (rs, rowNum) -> new OutboxTableStats(
                table.getTableName(),
                rs.getLong("init_count"),
                rs.getLong("retryable_failed_count"),
                rs.getLong("dead_letter_count"),
                rs.getLong("sent_count")
        ));
    }

    private String statsSql(BluenoteMqProperties.OutboxTable table) {
        String status = column(table.getStatusColumn());
        String retry = column(table.getRetryCountColumn());
        String nextRetryAt = column(table.getNextRetryAtColumn());
        int maxRetry = properties.getOutbox().getMaxRetry();
        return "SELECT "
                + "SUM(CASE WHEN " + status + " = 'INIT' THEN 1 ELSE 0 END) AS init_count, "
                + "SUM(CASE WHEN " + status + " = 'FAILED' AND " + retry + " < " + maxRetry
                + " AND (" + nextRetryAt + " IS NULL OR " + nextRetryAt + " <= NOW(3)) THEN 1 ELSE 0 END) AS retryable_failed_count, "
                + "SUM(CASE WHEN " + status + " = 'FAILED' AND " + retry + " >= " + maxRetry + " THEN 1 ELSE 0 END) AS dead_letter_count, "
                + "SUM(CASE WHEN " + status + " = 'SENT' THEN 1 ELSE 0 END) AS sent_count "
                + "FROM " + tableName(table.getTableName());
    }

    private String selectSql(BluenoteMqProperties.OutboxTable table) {
        return "SELECT "
                + column(table.getEventIdColumn()) + " AS event_id, "
                + column(table.getEventTypeColumn()) + " AS event_type, "
                + column(table.getAggregateIdColumn()) + " AS aggregate_id, "
                + column(table.getPayloadColumn()) + " AS payload, "
                + column(table.getRetryCountColumn()) + " AS retry_count "
                + "FROM " + tableName(table.getTableName()) + " "
                + "WHERE " + column(table.getStatusColumn()) + " IN ('INIT', 'FAILED') "
                + "AND (" + column(table.getNextRetryAtColumn()) + " IS NULL OR "
                + column(table.getNextRetryAtColumn()) + " <= NOW(3)) "
                + "AND " + column(table.getRetryCountColumn()) + " < " + properties.getOutbox().getMaxRetry() + " "
                + "ORDER BY " + column(table.getCreatedAtColumn()) + " "
                + "LIMIT " + properties.getOutbox().getBatchSize();
    }

    private OutboxEventRow row(BluenoteMqProperties.OutboxTable table, ResultSet rs) throws SQLException {
        return new OutboxEventRow(
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("aggregate_id"),
                rs.getString("payload"),
                Math.max(0, rs.getInt("retry_count"))
        );
    }

    private void markSent(BluenoteMqProperties.OutboxTable table, OutboxEventRow row) {
        jdbcTemplate.update(
                "UPDATE " + tableName(table.getTableName()) + " SET "
                        + column(table.getStatusColumn()) + " = 'SENT', "
                        + column(table.getUpdatedAtColumn()) + " = NOW(3) "
                        + "WHERE " + column(table.getEventIdColumn()) + " = ?",
                row.eventId()
        );
    }

    private void markFailure(BluenoteMqProperties.OutboxTable table, OutboxEventRow row, Exception exception) {
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(nextRetryDelaySeconds(row.retryCount()));
        jdbcTemplate.update(
                "UPDATE " + tableName(table.getTableName()) + " SET "
                        + column(table.getStatusColumn()) + " = 'FAILED', "
                        + column(table.getRetryCountColumn()) + " = " + column(table.getRetryCountColumn()) + " + 1, "
                        + column(table.getNextRetryAtColumn()) + " = ?, "
                        + column(table.getUpdatedAtColumn()) + " = NOW(3) "
                        + "WHERE " + column(table.getEventIdColumn()) + " = ?",
                nextRetryAt,
                row.eventId()
        );
    }

    private long nextRetryDelaySeconds(int retryCount) {
        long base = Math.max(1, properties.getOutbox().getRetryBaseDelaySeconds());
        long delay = base * (1L << Math.min(Math.max(0, retryCount), 6));
        return Math.min(delay, Math.max(base, properties.getOutbox().getRetryMaxDelaySeconds()));
    }

    private void validate(BluenoteMqProperties.OutboxTable table) {
        tableName(table.getTableName());
        column(table.getStatusColumn());
        column(table.getEventIdColumn());
        column(table.getEventTypeColumn());
        column(table.getAggregateIdColumn());
        column(table.getPayloadColumn());
        column(table.getRetryCountColumn());
        column(table.getNextRetryAtColumn());
        column(table.getCreatedAtColumn());
        column(table.getUpdatedAtColumn());
    }

    private String tableName(String value) {
        if (value == null || value.isBlank() || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid outbox table name: " + value);
        }
        return value;
    }

    private String column(String value) {
        if (value == null || value.isBlank() || !SQL_IDENTIFIER.matcher(value).matches() || value.contains(".")) {
            throw new IllegalArgumentException("Invalid outbox column name: " + value);
        }
        return value;
    }

    private record OutboxEventRow(
            String eventId,
            String eventType,
            String aggregateId,
            String payload,
            int retryCount
    ) {
    }
}
