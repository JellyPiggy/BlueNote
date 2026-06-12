-- BlueNote feed schema for the second main chain.
-- Contract source: docs/contracts/db/02-social-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_feed
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_feed;

CREATE TABLE IF NOT EXISTS feed_note_index (
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Author user ID',
  title_snapshot VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Title snapshot',
  content_preview_snapshot VARCHAR(256) NOT NULL DEFAULT '' COMMENT 'Content preview snapshot',
  cover_url_snapshot VARCHAR(512) NULL COMMENT 'Cover URL snapshot',
  visibility VARCHAR(32) NOT NULL COMMENT 'PUBLIC / PRIVATE',
  note_status VARCHAR(32) NOT NULL COMMENT 'PUBLISHED / DELETED / OFFLINE',
  item_status VARCHAR(32) NOT NULL COMMENT 'VISIBLE / HIDDEN / DELETED',
  published_at DATETIME(3) NOT NULL COMMENT 'Published time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (note_id),
  KEY idx_feed_author_time (author_id, published_at, note_id),
  KEY idx_feed_status_time (item_status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed note lightweight index';

CREATE TABLE IF NOT EXISTS feed_inbox_item (
  id BIGINT NOT NULL COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'Inbox owner user ID',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Author user ID',
  published_at DATETIME(3) NOT NULL COMMENT 'Published time',
  source_type VARCHAR(32) NOT NULL COMMENT 'PUSH / PULL / FOLLOW_BACKFILL',
  item_status VARCHAR(32) NOT NULL COMMENT 'VISIBLE / HIDDEN / DELETED',
  delivered_at DATETIME(3) NOT NULL COMMENT 'Delivered time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_feed_inbox_user_note (user_id, note_id),
  KEY idx_feed_inbox_user_time (user_id, published_at, note_id),
  KEY idx_feed_inbox_user_author (user_id, author_id, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed inbox snapshot';

CREATE TABLE IF NOT EXISTS feed_author_strategy (
  author_id BIGINT NOT NULL COMMENT 'Author user ID',
  strategy_type VARCHAR(32) NOT NULL COMMENT 'NORMAL / BIG_AUTHOR',
  follower_count_snapshot BIGINT NOT NULL DEFAULT 0 COMMENT 'Follower count snapshot',
  evaluated_at DATETIME(3) NOT NULL COMMENT 'Strategy evaluated time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (author_id),
  KEY idx_feed_author_strategy (strategy_type, evaluated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed author fanout strategy';

CREATE TABLE IF NOT EXISTS feed_fanout_task (
  task_id VARCHAR(128) NOT NULL COMMENT 'Task ID',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Author user ID',
  source_event_id VARCHAR(128) NOT NULL COMMENT 'Source event ID',
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  target_count INT NOT NULL DEFAULT 0 COMMENT 'Target follower count',
  success_count INT NOT NULL DEFAULT 0 COMMENT 'Successful delivered count',
  failed_count INT NOT NULL DEFAULT 0 COMMENT 'Failed delivered count',
  last_error VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id),
  KEY idx_feed_fanout_status (task_status, updated_at),
  KEY idx_feed_fanout_note (note_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed fanout task';

CREATE TABLE IF NOT EXISTS feed_fanout_sub_task (
  sub_task_id VARCHAR(128) NOT NULL COMMENT 'Sub task ID',
  task_id VARCHAR(128) NOT NULL COMMENT 'Task ID',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Author user ID',
  published_at DATETIME(3) NOT NULL COMMENT 'Published time',
  target_user_ids_json JSON NOT NULL COMMENT 'Target user IDs',
  progress_user_id BIGINT NULL COMMENT 'Last processed user ID',
  sub_task_status VARCHAR(32) NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  message_status VARCHAR(32) NOT NULL COMMENT 'PENDING / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  last_error VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (sub_task_id),
  KEY idx_feed_sub_task_status (task_id, sub_task_status),
  KEY idx_feed_sub_message_status (message_status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed fanout sub task';

CREATE TABLE IF NOT EXISTS feed_rebuild_task (
  task_id VARCHAR(128) NOT NULL COMMENT 'Task ID',
  user_id BIGINT NOT NULL COMMENT 'Inbox owner user ID',
  reason VARCHAR(64) NOT NULL COMMENT 'Rebuild reason',
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  progress_json JSON NOT NULL COMMENT 'Progress snapshot',
  last_error VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id),
  KEY idx_feed_rebuild_status (task_status, updated_at),
  KEY idx_feed_rebuild_user (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed rebuild task';

CREATE TABLE IF NOT EXISTS feed_cleanup_task (
  task_id VARCHAR(128) NOT NULL COMMENT 'Task ID',
  cleanup_type VARCHAR(32) NOT NULL COMMENT 'UNFOLLOW / NOTE_HIDDEN / NOTE_DELETED',
  user_id BIGINT NULL COMMENT 'Inbox owner user ID',
  author_id BIGINT NULL COMMENT 'Author user ID',
  note_id BIGINT NULL COMMENT 'Note ID',
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  progress_json JSON NOT NULL COMMENT 'Progress snapshot',
  last_error VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id),
  KEY idx_feed_cleanup_status (task_status, updated_at),
  KEY idx_feed_cleanup_user_author (user_id, author_id, updated_at),
  KEY idx_feed_cleanup_note (note_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed cleanup task';

CREATE TABLE IF NOT EXISTS feed_consume_record (
  id BIGINT NOT NULL COMMENT 'Primary key',
  consumer_group VARCHAR(128) NOT NULL COMMENT 'Consumer group',
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
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
  UNIQUE KEY uk_feed_consumer_event (consumer_group, event_id),
  KEY idx_feed_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed consume record';

CREATE TABLE IF NOT EXISTS feed_outbox_event (
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'FeedDelivered / FeedRebuilt',
  aggregate_id VARCHAR(128) NOT NULL COMMENT 'Aggregate ID',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_feed_outbox_status_retry (send_status, next_retry_at),
  KEY idx_feed_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feed outbox event';
