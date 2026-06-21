-- BlueNote comment schema for the second main chain.
-- Contract source: docs/contracts/db/02-social-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_comment
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_comment;

CREATE TABLE IF NOT EXISTS content_comment (
  comment_id BIGINT NOT NULL COMMENT 'Comment ID',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  note_author_id BIGINT NOT NULL COMMENT 'Note author user ID',
  user_id BIGINT NOT NULL COMMENT 'Comment author user ID',
  root_id BIGINT NOT NULL COMMENT 'Root comment ID',
  parent_comment_id BIGINT NULL COMMENT 'Parent comment ID',
  reply_to_user_id BIGINT NULL COMMENT 'Replied user ID',
  level INT NOT NULL COMMENT '1 root comment, 2 reply',
  comment_status VARCHAR(32) NOT NULL COMMENT 'VISIBLE / DELETED / HIDDEN',
  like_count_snapshot BIGINT NOT NULL DEFAULT 0,
  reply_count_snapshot BIGINT NOT NULL DEFAULT 0,
  hot_score_snapshot BIGINT NOT NULL DEFAULT 0 COMMENT 'reply_count_snapshot * 6 + like_count_snapshot * 4',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (comment_id),
  KEY idx_comment_note_root_time (note_id, level, comment_status, created_at, comment_id),
  KEY idx_comment_note_root_hot (note_id, level, comment_status, hot_score_snapshot, created_at, comment_id),
  KEY idx_comment_root_time (root_id, level, comment_status, created_at, comment_id),
  KEY idx_comment_user_time (user_id, created_at, comment_id),
  KEY idx_comment_note_author (note_author_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comment fact';

CREATE TABLE IF NOT EXISTS user_comment (
  id BIGINT NOT NULL COMMENT 'Primary key',
  comment_id BIGINT NOT NULL COMMENT 'Comment ID',
  user_id BIGINT NOT NULL COMMENT 'Comment author user ID',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  root_id BIGINT NOT NULL COMMENT 'Root comment ID',
  parent_comment_id BIGINT NULL COMMENT 'Parent comment ID',
  comment_status VARCHAR(32) NOT NULL COMMENT 'VISIBLE / DELETED / HIDDEN',
  content_preview VARCHAR(256) NOT NULL,
  note_title_snapshot VARCHAR(128) NULL,
  note_cover_url_snapshot VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_comment_comment (comment_id),
  KEY idx_user_comment_list (user_id, created_at, comment_id),
  KEY idx_user_comment_root (root_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User comment read model';

CREATE TABLE IF NOT EXISTS comment_content (
  comment_id BIGINT NOT NULL COMMENT 'Comment ID',
  content TEXT NOT NULL,
  content_preview VARCHAR(256) NOT NULL,
  audit_status VARCHAR(32) NOT NULL COMMENT 'SKIPPED / PENDING / PASSED / REJECTED',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comment content';

CREATE TABLE IF NOT EXISTS comment_like (
  id BIGINT NOT NULL COMMENT 'Primary key',
  comment_id BIGINT NOT NULL COMMENT 'Comment ID',
  comment_user_id BIGINT NOT NULL COMMENT 'Comment author user ID',
  user_id BIGINT NOT NULL COMMENT 'Like user ID',
  like_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / CANCELED',
  liked_at DATETIME(3) NULL,
  canceled_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_like_user (comment_id, user_id),
  KEY idx_comment_like_comment (comment_id, like_status, liked_at),
  KEY idx_comment_like_user (user_id, like_status, liked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comment like detail';

CREATE TABLE IF NOT EXISTS comment_idempotent_request (
  idempotent_key VARCHAR(128) NOT NULL COMMENT 'Idempotency key',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  operation VARCHAR(64) NOT NULL COMMENT 'Operation name',
  biz_id BIGINT NULL COMMENT 'Related business ID',
  request_hash VARCHAR(128) NOT NULL,
  request_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING / SUCCESS / FAIL',
  response_payload JSON NULL COMMENT 'Successful response snapshot',
  expire_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (idempotent_key),
  KEY idx_comment_idem_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comment idempotent request';

CREATE TABLE IF NOT EXISTS comment_operation_log (
  id BIGINT NOT NULL COMMENT 'Primary key',
  comment_id BIGINT NOT NULL COMMENT 'Comment ID',
  user_id BIGINT NOT NULL COMMENT 'Operator user ID',
  operation_type VARCHAR(32) NOT NULL COMMENT 'CREATE / REPLY / DELETE / HIDE',
  before_status VARCHAR(32) NULL,
  after_status VARCHAR(32) NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_comment_operation_comment (comment_id, created_at),
  KEY idx_comment_operation_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comment operation log';

CREATE TABLE IF NOT EXISTS comment_outbox_event (
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'CommentCreated / CommentDeleted / CommentLiked / CommentUnliked',
  aggregate_id BIGINT NOT NULL COMMENT 'Aggregate ID, normally commentId',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_comment_outbox_status_retry (send_status, next_retry_at),
  KEY idx_comment_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comment outbox event';

CREATE TABLE IF NOT EXISTS comment_consume_record (
  id BIGINT NOT NULL COMMENT 'Primary key',
  consumer_group VARCHAR(128) NOT NULL COMMENT 'Consumer group',
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  topic VARCHAR(64) NOT NULL COMMENT 'Topic',
  event_type VARCHAR(64) NOT NULL COMMENT 'Event type',
  biz_key VARCHAR(128) NULL COMMENT 'Business key',
  consume_status VARCHAR(32) NOT NULL COMMENT 'SUCCESS / FAIL',
  retry_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(512) NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_consumer_event (consumer_group, event_id),
  KEY idx_comment_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comment consume record';
