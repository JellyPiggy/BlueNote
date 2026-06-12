CREATE DATABASE IF NOT EXISTS bluenote_notification
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_notification;

CREATE TABLE IF NOT EXISTS notification_record (
  notification_id BIGINT NOT NULL COMMENT 'Notification ID',
  receiver_id BIGINT NOT NULL COMMENT 'Receiver user ID',
  actor_id BIGINT NULL COMMENT 'Latest actor user ID',
  category VARCHAR(32) NOT NULL COMMENT 'INTERACTION / FOLLOW / SYSTEM / ORDER',
  notification_type VARCHAR(64) NOT NULL COMMENT 'Notification type',
  target_type VARCHAR(32) NOT NULL COMMENT 'Target type',
  target_id VARCHAR(64) NOT NULL COMMENT 'Target ID',
  source_type VARCHAR(32) NOT NULL COMMENT 'Source type',
  source_id VARCHAR(64) NOT NULL COMMENT 'Source business ID',
  aggregate TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether aggregated',
  aggregate_key VARCHAR(128) NULL COMMENT 'Stable aggregate key',
  aggregate_unread_key VARCHAR(128) NULL COMMENT 'Unread aggregate unique key, null after read',
  actor_count INT NOT NULL DEFAULT 1 COMMENT 'Distinct actor count',
  title VARCHAR(128) NOT NULL COMMENT 'Display title',
  content VARCHAR(512) NOT NULL COMMENT 'Display content',
  snapshot_json JSON NOT NULL COMMENT 'Display snapshot',
  jump_json JSON NOT NULL COMMENT 'Mobile jump parameters',
  read_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 unread, 1 read',
  visible_status TINYINT NOT NULL DEFAULT 1 COMMENT '1 visible, 2 deleted, 3 archived',
  last_event_at DATETIME(3) NOT NULL COMMENT 'Latest event time',
  read_at DATETIME(3) NULL COMMENT 'Read time',
  expire_at DATETIME(3) NULL COMMENT 'Expire time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (notification_id),
  UNIQUE KEY uk_notify_aggregate_unread (receiver_id, aggregate_unread_key),
  KEY idx_notify_receiver_category_time (receiver_id, category, visible_status, last_event_at, notification_id),
  KEY idx_notify_receiver_time (receiver_id, visible_status, last_event_at, notification_id),
  KEY idx_notify_receiver_source (receiver_id, source_type, source_id, notification_type),
  KEY idx_notify_expire (expire_at, visible_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification record';

CREATE TABLE IF NOT EXISTS notification_aggregate_actor (
  id BIGINT NOT NULL COMMENT 'Primary key',
  notification_id BIGINT NOT NULL COMMENT 'Notification ID',
  actor_id BIGINT NOT NULL COMMENT 'Actor user ID',
  source_biz_id VARCHAR(128) NOT NULL COMMENT 'Source business ID',
  acted_at DATETIME(3) NOT NULL COMMENT 'Actor event time',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notify_actor (notification_id, actor_id, source_biz_id),
  KEY idx_notify_actor_time (notification_id, acted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification aggregate actors';

CREATE TABLE IF NOT EXISTS notification_unread_counter (
  user_id BIGINT NOT NULL COMMENT 'User ID',
  category VARCHAR(32) NOT NULL COMMENT 'Notification category',
  unread_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Unread count',
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id, category),
  UNIQUE KEY uk_notify_unread_user_category (user_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification unread counter';

CREATE TABLE IF NOT EXISTS notification_consume_record (
  id BIGINT NOT NULL COMMENT 'Primary key',
  consumer_group VARCHAR(128) NOT NULL COMMENT 'Consumer group',
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
  topic VARCHAR(64) NOT NULL COMMENT 'Topic',
  event_type VARCHAR(64) NOT NULL COMMENT 'Event type',
  biz_key VARCHAR(128) NULL COMMENT 'Business key',
  envelope_json JSON NULL COMMENT 'Original event envelope for replay',
  consume_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING / SUCCESS / FAIL / SKIPPED',
  retry_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(512) NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_consumer_event (consumer_group, event_id),
  KEY idx_notification_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification consume record';

CREATE TABLE IF NOT EXISTS notification_outbox_event (
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'Notification event type',
  aggregate_id VARCHAR(128) NOT NULL COMMENT 'Aggregate ID',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_notification_outbox_status_retry (send_status, next_retry_at),
  KEY idx_notification_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification outbox event';
