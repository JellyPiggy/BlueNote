-- BlueNote relation schema for the second main chain.
-- Contract source: docs/contracts/db/02-social-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_relation
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_relation;

CREATE TABLE IF NOT EXISTS relation_following (
  id BIGINT NOT NULL COMMENT 'Primary key',
  follower_id BIGINT NOT NULL COMMENT 'Follower user ID',
  followee_id BIGINT NOT NULL COMMENT 'Followee user ID',
  relation_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / CANCELED',
  followed_at DATETIME(3) NULL,
  canceled_at DATETIME(3) NULL,
  relation_version BIGINT NOT NULL COMMENT 'Relation version',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_following_pair (follower_id, followee_id),
  KEY idx_following_list (follower_id, relation_status, followed_at, followee_id),
  KEY idx_following_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Relation following fact';

CREATE TABLE IF NOT EXISTS relation_follower (
  id BIGINT NOT NULL COMMENT 'Primary key',
  followee_id BIGINT NOT NULL COMMENT 'Followee user ID',
  follower_id BIGINT NOT NULL COMMENT 'Follower user ID',
  relation_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / CANCELED',
  followed_at DATETIME(3) NULL,
  canceled_at DATETIME(3) NULL,
  relation_version BIGINT NOT NULL COMMENT 'Relation version',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_follower_pair (followee_id, follower_id),
  KEY idx_follower_list (followee_id, relation_status, followed_at, follower_id),
  KEY idx_follower_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Relation follower read model';

CREATE TABLE IF NOT EXISTS relation_change_log (
  change_id BIGINT NOT NULL COMMENT 'Change ID',
  follower_id BIGINT NOT NULL COMMENT 'Follower user ID',
  followee_id BIGINT NOT NULL COMMENT 'Followee user ID',
  action_type VARCHAR(32) NOT NULL COMMENT 'FOLLOW / UNFOLLOW',
  before_status VARCHAR(32) NULL COMMENT 'Before status',
  after_status VARCHAR(32) NOT NULL COMMENT 'After status',
  relation_version BIGINT NOT NULL COMMENT 'Relation version after change',
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (change_id),
  KEY idx_relation_change_pair (follower_id, followee_id, created_at),
  KEY idx_relation_change_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Relation change log';

CREATE TABLE IF NOT EXISTS relation_outbox_event (
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'UserFollowed / UserUnfollowed',
  aggregate_id BIGINT NOT NULL COMMENT 'Aggregate ID, normally followerId',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_relation_outbox_status_retry (send_status, next_retry_at),
  KEY idx_relation_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Relation outbox event';

CREATE TABLE IF NOT EXISTS relation_consume_record (
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
  UNIQUE KEY uk_relation_consumer_event (consumer_group, event_id),
  KEY idx_relation_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Relation consume record';
