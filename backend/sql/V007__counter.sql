-- BlueNote counter schema for the second main chain.
-- Contract source: docs/contracts/db/02-social-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_counter
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_counter;

CREATE TABLE IF NOT EXISTS counter_snapshot (
  id BIGINT NOT NULL COMMENT 'Primary key',
  target_type VARCHAR(32) NOT NULL COMMENT 'NOTE / USER / COMMENT',
  target_id BIGINT NOT NULL COMMENT 'Target ID',
  counter_field VARCHAR(64) NOT NULL COMMENT 'Counter field',
  counter_value BIGINT NOT NULL DEFAULT 0 COMMENT 'Current value',
  snapshot_version BIGINT NOT NULL COMMENT 'Snapshot version',
  flushed_at DATETIME(3) NOT NULL COMMENT 'Last flush time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_counter_target_field (target_type, target_id, counter_field),
  KEY idx_counter_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Counter snapshot';

CREATE TABLE IF NOT EXISTS counter_delta_log (
  delta_id VARCHAR(128) NOT NULL COMMENT 'Delta ID',
  source_event_id VARCHAR(64) NOT NULL COMMENT 'Source event ID',
  source_event_type VARCHAR(64) NOT NULL COMMENT 'Source event type',
  target_type VARCHAR(32) NOT NULL COMMENT 'NOTE / USER / COMMENT',
  target_id BIGINT NOT NULL COMMENT 'Target ID',
  counter_field VARCHAR(64) NOT NULL COMMENT 'Counter field',
  delta_value BIGINT NOT NULL COMMENT 'Delta value',
  apply_status VARCHAR(32) NOT NULL COMMENT 'PENDING / APPLIED / FAILED',
  occurred_at DATETIME(3) NOT NULL COMMENT 'Source event time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (delta_id),
  KEY idx_counter_delta_status (apply_status, updated_at),
  KEY idx_counter_delta_target (target_type, target_id, counter_field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Counter delta log';

CREATE TABLE IF NOT EXISTS counter_rebuild_task (
  task_id VARCHAR(128) NOT NULL COMMENT 'Task ID',
  task_type VARCHAR(32) NOT NULL COMMENT 'RECONCILE / WARMUP',
  target_type VARCHAR(32) NOT NULL COMMENT 'NOTE / USER / COMMENT',
  target_id BIGINT NOT NULL COMMENT 'Target ID',
  fields_json JSON NOT NULL COMMENT 'Counter fields',
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  progress_json JSON NOT NULL COMMENT 'Progress snapshot',
  last_error VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id),
  KEY idx_counter_rebuild_status (task_status, updated_at),
  KEY idx_counter_rebuild_target (target_type, target_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Counter rebuild task';

CREATE TABLE IF NOT EXISTS counter_outbox_event (
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'CounterDeltaCreated / CounterChanged / CounterRebuilt',
  aggregate_id VARCHAR(128) NOT NULL COMMENT 'Aggregate ID, normally targetType:targetId',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_counter_outbox_status_retry (send_status, next_retry_at),
  KEY idx_counter_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Counter outbox event';

CREATE TABLE IF NOT EXISTS counter_consume_record (
  id BIGINT NOT NULL COMMENT 'Primary key',
  consumer_group VARCHAR(128) NOT NULL COMMENT 'Consumer group',
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  topic VARCHAR(64) NOT NULL COMMENT 'Topic',
  event_type VARCHAR(64) NOT NULL COMMENT 'Event type',
  biz_key VARCHAR(128) NULL COMMENT 'Business key',
  consume_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING / SUCCESS / FAIL',
  retry_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(512) NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_counter_consumer_event (consumer_group, event_id),
  KEY idx_counter_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Counter consume record';
