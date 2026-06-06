-- BlueNote user schema for the first main chain.
-- Contract source: docs/contracts/db/01-main-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_user
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_user;

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT NOT NULL COMMENT 'User ID',
  bluenote_no VARCHAR(32) NOT NULL COMMENT 'BlueNote number',
  nickname VARCHAR(64) NOT NULL COMMENT 'Nickname',
  avatar_file_id BIGINT NULL COMMENT 'Avatar file ID',
  avatar_url VARCHAR(512) NULL COMMENT 'Avatar URL snapshot',
  bio VARCHAR(256) NULL COMMENT 'Bio',
  gender VARCHAR(16) NOT NULL COMMENT 'UNKNOWN / MALE / FEMALE',
  birthday DATE NULL,
  region_code VARCHAR(32) NULL,
  home_cover_file_id BIGINT NULL COMMENT 'Home cover file ID',
  home_cover_url VARCHAR(512) NULL COMMENT 'Home cover URL snapshot',
  user_status VARCHAR(32) NOT NULL COMMENT 'NORMAL / DISABLED / DELETED',
  profile_version BIGINT NOT NULL COMMENT 'Profile version',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_profile_bluenote_no (bluenote_no),
  KEY idx_user_profile_status (user_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User profile';

CREATE TABLE IF NOT EXISTS user_profile_audit (
  id BIGINT NOT NULL COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  field_name VARCHAR(64) NOT NULL COMMENT 'Changed field',
  old_value_mask VARCHAR(512) NULL COMMENT 'Masked old value',
  new_value_mask VARCHAR(512) NULL COMMENT 'Masked new value',
  operator_id BIGINT NOT NULL COMMENT 'Operator ID',
  operator_type VARCHAR(32) NOT NULL COMMENT 'USER / ADMIN / SYSTEM',
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_audit_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User profile audit';

CREATE TABLE IF NOT EXISTS user_outbox_event (
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'Event type',
  aggregate_id BIGINT NOT NULL COMMENT 'Aggregate ID, normally userId',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_user_outbox_status_retry (send_status, next_retry_at),
  KEY idx_user_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User outbox event';
