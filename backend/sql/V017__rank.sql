-- BlueNote rank schema for ranking foundation.
-- Contract source: docs/contracts/db/02-social-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_rank
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_rank;

CREATE TABLE IF NOT EXISTS rank_definition (
  rank_code VARCHAR(64) NOT NULL COMMENT 'Rank code',
  rank_name VARCHAR(128) NOT NULL COMMENT 'Rank display name',
  member_type VARCHAR(32) NOT NULL COMMENT 'NOTE / USER',
  period_type VARCHAR(32) NOT NULL COMMENT 'WEEKLY / YEARLY',
  score_rule_json JSON NOT NULL COMMENT 'Score rule',
  top_limit INT NOT NULL DEFAULT 100,
  redis_limit INT NOT NULL DEFAULT 120,
  estimate_enabled TINYINT NOT NULL DEFAULT 0,
  public_visible TINYINT NOT NULL DEFAULT 1,
  definition_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / DISABLED',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (rank_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank definition';

CREATE TABLE IF NOT EXISTS rank_period (
  id BIGINT NOT NULL COMMENT 'Primary key',
  rank_code VARCHAR(64) NOT NULL,
  period_id VARCHAR(32) NOT NULL,
  period_type VARCHAR(32) NOT NULL,
  period_status VARCHAR(32) NOT NULL COMMENT 'OPEN / FROZEN',
  start_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  frozen_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rank_period (rank_code, period_id),
  KEY idx_rank_period_status (rank_code, period_status, start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank period';

CREATE TABLE IF NOT EXISTS rank_note_index (
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Author user ID',
  title_snapshot VARCHAR(128) NOT NULL DEFAULT '',
  cover_url_snapshot VARCHAR(512) NULL,
  visibility VARCHAR(32) NOT NULL COMMENT 'PUBLIC / PRIVATE',
  note_status VARCHAR(32) NOT NULL COMMENT 'PUBLISHED / DELETED / OFFLINE',
  eligible_status VARCHAR(32) NOT NULL COMMENT 'ELIGIBLE / INELIGIBLE',
  published_at DATETIME(3) NOT NULL,
  last_event_id VARCHAR(128) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (note_id),
  KEY idx_rank_note_author (author_id, published_at),
  KEY idx_rank_note_eligible (eligible_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank note lightweight index';

CREATE TABLE IF NOT EXISTS rank_member_score (
  id BIGINT NOT NULL COMMENT 'Primary key',
  rank_code VARCHAR(64) NOT NULL,
  period_id VARCHAR(32) NOT NULL,
  member_type VARCHAR(32) NOT NULL COMMENT 'NOTE / USER',
  member_id BIGINT NOT NULL,
  score BIGINT NOT NULL DEFAULT 0 COMMENT 'Real integer score',
  rank_score DOUBLE NOT NULL DEFAULT 0 COMMENT 'Redis score with tie breaker',
  score_version INT NOT NULL DEFAULT 1,
  member_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / SUSPENDED',
  last_score_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rank_member_score (rank_code, period_id, member_type, member_id),
  KEY idx_rank_score_top (rank_code, period_id, score, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank member score';

CREATE TABLE IF NOT EXISTS rank_score_contribution (
  id BIGINT NOT NULL COMMENT 'Primary key',
  rank_code VARCHAR(64) NOT NULL,
  period_id VARCHAR(32) NOT NULL,
  member_type VARCHAR(32) NOT NULL COMMENT 'NOTE / USER',
  member_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL COMMENT 'NOTE',
  source_id BIGINT NOT NULL,
  score BIGINT NOT NULL DEFAULT 0,
  contribution_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / SUSPENDED',
  last_event_id VARCHAR(128) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rank_contribution (rank_code, period_id, member_type, member_id, source_type, source_id),
  KEY idx_rank_contribution_source (source_type, source_id, period_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank score contribution';

CREATE TABLE IF NOT EXISTS rank_score_change_log (
  change_id BIGINT NOT NULL COMMENT 'Change ID',
  source_event_id VARCHAR(128) NOT NULL,
  source_event_type VARCHAR(64) NOT NULL,
  rank_code VARCHAR(64) NOT NULL,
  period_id VARCHAR(32) NOT NULL,
  member_type VARCHAR(32) NOT NULL,
  member_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  field_name VARCHAR(64) NOT NULL,
  delta_value BIGINT NOT NULL DEFAULT 0,
  score_delta BIGINT NOT NULL DEFAULT 0,
  score_after BIGINT NOT NULL DEFAULT 0,
  occurred_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (change_id),
  UNIQUE KEY uk_rank_score_change (source_event_id, rank_code, member_type, member_id, field_name),
  KEY idx_rank_score_log_period (rank_code, period_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank score change log';

CREATE TABLE IF NOT EXISTS rank_snapshot (
  snapshot_id BIGINT NOT NULL COMMENT 'Snapshot ID',
  rank_code VARCHAR(64) NOT NULL,
  period_id VARCHAR(32) NOT NULL,
  snapshot_type VARCHAR(32) NOT NULL COMMENT 'SCHEDULED / MANUAL / REBUILD',
  item_count INT NOT NULL DEFAULT 0,
  source_version VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (snapshot_id),
  KEY idx_rank_snapshot_latest (rank_code, period_id, snapshot_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank snapshot';

CREATE TABLE IF NOT EXISTS rank_snapshot_item (
  id BIGINT NOT NULL COMMENT 'Primary key',
  snapshot_id BIGINT NOT NULL,
  rank_no INT NOT NULL,
  member_type VARCHAR(32) NOT NULL,
  member_id BIGINT NOT NULL,
  score BIGINT NOT NULL DEFAULT 0,
  rank_score DOUBLE NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rank_snapshot_item (snapshot_id, rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank snapshot item';

CREATE TABLE IF NOT EXISTS rank_event_consume_log (
  id BIGINT NOT NULL COMMENT 'Primary key',
  consumer_group VARCHAR(128) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  topic VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  biz_key VARCHAR(128) NULL,
  consume_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING / SUCCESS / FAIL',
  retry_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(512) NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rank_consume (consumer_group, event_id),
  KEY idx_rank_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank event consume log';

CREATE TABLE IF NOT EXISTS rank_outbox_event (
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'RankChanged / RankFrozen',
  aggregate_id VARCHAR(128) NOT NULL COMMENT 'rankCode:periodId',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_rank_outbox_status_retry (send_status, next_retry_at),
  KEY idx_rank_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank outbox event';

CREATE TABLE IF NOT EXISTS rank_rebuild_task (
  task_id VARCHAR(128) NOT NULL COMMENT 'Task ID',
  task_type VARCHAR(32) NOT NULL COMMENT 'REBUILD_REDIS / SNAPSHOT / RECALCULATE',
  rank_code VARCHAR(64) NOT NULL,
  period_id VARCHAR(32) NOT NULL,
  task_status VARCHAR(32) NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  progress_json JSON NOT NULL,
  reason VARCHAR(128) NULL,
  error_message VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_id),
  KEY idx_rank_task_status (task_status, created_at),
  KEY idx_rank_task_rank (rank_code, period_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rank rebuild task';

INSERT INTO rank_definition (
  rank_code, rank_name, member_type, period_type, score_rule_json, top_limit,
  redis_limit, estimate_enabled, public_visible, definition_status, created_at, updated_at
) VALUES
  (
    'WEEKLY_HOT_NOTE', '本周热门笔记榜', 'NOTE', 'WEEKLY',
    JSON_OBJECT('scoreVersion', 1, 'like_count', 1, 'comment_count', 2, 'collect_count', 3),
    100, 120, 0, 1, 'ACTIVE', NOW(3), NOW(3)
  ),
  (
    'YEARLY_CREATOR_GROWTH', '本年创作者成长榜', 'USER', 'YEARLY',
    JSON_OBJECT('scoreVersion', 1, 'like_count', 1, 'comment_count', 2, 'collect_count', 3),
    100, 120, 1, 1, 'ACTIVE', NOW(3), NOW(3)
  )
ON DUPLICATE KEY UPDATE
  rank_name = VALUES(rank_name),
  member_type = VALUES(member_type),
  period_type = VALUES(period_type),
  score_rule_json = VALUES(score_rule_json),
  top_limit = VALUES(top_limit),
  redis_limit = VALUES(redis_limit),
  estimate_enabled = VALUES(estimate_enabled),
  public_visible = VALUES(public_visible),
  definition_status = VALUES(definition_status),
  updated_at = NOW(3);
