-- BlueNote push schema for realtime delivery foundation.
-- Contract source: docs/contracts/api/12-push-api.md

CREATE DATABASE IF NOT EXISTS bluenote_push
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_push;

CREATE TABLE IF NOT EXISTS push_device (
  device_id VARCHAR(128) NOT NULL COMMENT 'Client generated device ID',
  user_id BIGINT NOT NULL COMMENT 'Bound user ID',
  platform VARCHAR(32) NOT NULL COMMENT 'IOS / ANDROID / H5',
  push_provider VARCHAR(32) NOT NULL COMMENT 'UNI_PUSH / APNS / FCM / VENDOR_PUSH / NOOP',
  provider_client_id VARCHAR(256) NULL COMMENT 'Provider token or uni-push clientId',
  app_version VARCHAR(64) NULL COMMENT 'App version',
  os_version VARCHAR(64) NULL COMMENT 'OS version',
  device_model VARCHAR(128) NULL COMMENT 'Device model',
  device_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / UNBOUND / DISABLED',
  registered_at DATETIME(3) NOT NULL COMMENT 'Register time',
  last_active_at DATETIME(3) NOT NULL COMMENT 'Last active time',
  unbound_at DATETIME(3) NULL COMMENT 'Unbind time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (device_id),
  KEY idx_push_device_user_status (user_id, device_status, last_active_at),
  KEY idx_push_device_provider (push_provider, provider_client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Push device binding';

CREATE TABLE IF NOT EXISTS push_preference (
  user_id BIGINT NOT NULL COMMENT 'User ID',
  global_enabled TINYINT NOT NULL DEFAULT 1,
  interaction_enabled TINYINT NOT NULL DEFAULT 1,
  follow_enabled TINYINT NOT NULL DEFAULT 1,
  system_enabled TINYINT NOT NULL DEFAULT 1,
  order_enabled TINYINT NOT NULL DEFAULT 1,
  im_enabled TINYINT NOT NULL DEFAULT 1,
  show_im_detail TINYINT NOT NULL DEFAULT 1,
  quiet_hours_enabled TINYINT NOT NULL DEFAULT 0,
  quiet_start VARCHAR(5) NULL COMMENT 'HH:mm',
  quiet_end VARCHAR(5) NULL COMMENT 'HH:mm',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Push user preference';

CREATE TABLE IF NOT EXISTS push_delivery_request (
  request_id VARCHAR(128) NOT NULL COMMENT 'Push request ID',
  source_service VARCHAR(64) NOT NULL COMMENT 'Source service',
  source_biz_type VARCHAR(64) NOT NULL COMMENT 'Source business type',
  source_biz_id VARCHAR(128) NOT NULL COMMENT 'Source business ID',
  scene VARCHAR(64) NOT NULL COMMENT 'Delivery scene',
  target_user_id BIGINT NOT NULL COMMENT 'Receiver user ID',
  target_device_policy VARCHAR(64) NOT NULL COMMENT 'Target device policy',
  delivery_strategy VARCHAR(64) NOT NULL COMMENT 'Delivery strategy',
  priority INT NOT NULL DEFAULT 5 COMMENT 'Priority',
  title VARCHAR(128) NOT NULL COMMENT 'Display title',
  body VARCHAR(512) NOT NULL COMMENT 'Display body',
  data_json JSON NOT NULL COMMENT 'Jump data',
  request_status VARCHAR(32) NOT NULL COMMENT 'RECEIVED / PROCESSING / DELIVERED / FILTERED / FAILED / EXPIRED',
  filtered_reason VARCHAR(128) NULL COMMENT 'Filtered reason',
  delivered_device_count INT NOT NULL DEFAULT 0 COMMENT 'Delivered device count',
  expire_at DATETIME(3) NULL COMMENT 'Expire time',
  completed_at DATETIME(3) NULL COMMENT 'Completed time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (request_id),
  UNIQUE KEY uk_push_source_biz (source_service, source_biz_type, source_biz_id, scene),
  KEY idx_push_request_user_time (target_user_id, created_at),
  KEY idx_push_request_status (request_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Push delivery request';

CREATE TABLE IF NOT EXISTS push_delivery_attempt (
  attempt_id BIGINT NOT NULL COMMENT 'Attempt ID',
  request_id VARCHAR(128) NOT NULL COMMENT 'Push request ID',
  target_user_id BIGINT NOT NULL COMMENT 'Receiver user ID',
  device_id VARCHAR(128) NULL COMMENT 'Target device ID',
  channel VARCHAR(32) NOT NULL COMMENT 'WEBSOCKET / UNI_PUSH / NOOP',
  attempt_status VARCHAR(32) NOT NULL COMMENT 'SUCCESS / SKIPPED / FAILED',
  skip_reason VARCHAR(128) NULL COMMENT 'Skip reason',
  provider_message_id VARCHAR(128) NULL COMMENT 'Provider message ID',
  error_message VARCHAR(512) NULL COMMENT 'Error message',
  attempted_at DATETIME(3) NOT NULL COMMENT 'Attempt time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (attempt_id),
  KEY idx_push_attempt_request (request_id, attempted_at),
  KEY idx_push_attempt_device (device_id, attempted_at),
  KEY idx_push_attempt_status (attempt_status, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Push delivery attempt';

CREATE TABLE IF NOT EXISTS push_click_log (
  id BIGINT NOT NULL COMMENT 'Primary key',
  request_id VARCHAR(128) NOT NULL COMMENT 'Push request ID',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  device_id VARCHAR(128) NULL COMMENT 'Device ID',
  data_json JSON NOT NULL COMMENT 'Click data',
  clicked_at DATETIME(3) NOT NULL COMMENT 'Click time',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_push_click_request (request_id, clicked_at),
  KEY idx_push_click_user (user_id, clicked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Push click log';

CREATE TABLE IF NOT EXISTS push_consume_record (
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
  UNIQUE KEY uk_push_consumer_event (consumer_group, event_id),
  KEY idx_push_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Push consume record';

CREATE TABLE IF NOT EXISTS push_outbox_event (
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'Push event type',
  aggregate_id VARCHAR(128) NOT NULL COMMENT 'Aggregate ID',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_push_outbox_status_retry (send_status, next_retry_at),
  KEY idx_push_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Push outbox event';
