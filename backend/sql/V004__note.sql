-- BlueNote note schema for the first main chain.
-- Contract source: docs/contracts/db/01-main-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_note
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_note;

CREATE TABLE IF NOT EXISTS note (
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Author user ID',
  note_type VARCHAR(32) NOT NULL COMMENT 'IMAGE_TEXT / VIDEO',
  note_status VARCHAR(32) NOT NULL COMMENT 'DRAFT / PUBLISHED / PRIVATE / AUDIT_REJECTED / OFFLINE / DELETED',
  visibility VARCHAR(32) NOT NULL COMMENT 'PUBLIC / PRIVATE',
  current_version INT NOT NULL COMMENT 'Current active version',
  latest_version INT NOT NULL COMMENT 'Latest version',
  cover_file_id BIGINT NULL COMMENT 'Cover file ID',
  comment_enabled TINYINT NOT NULL COMMENT 'Whether comments are enabled',
  published_at DATETIME(3) NULL,
  last_edited_at DATETIME(3) NULL,
  offline_reason VARCHAR(256) NULL,
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (note_id),
  KEY idx_note_author_status_time (author_id, note_status, published_at, note_id),
  KEY idx_note_status_visibility_time (note_status, visibility, published_at, note_id),
  KEY idx_note_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note';

CREATE TABLE IF NOT EXISTS note_version (
  id BIGINT NOT NULL COMMENT 'Primary key',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  version_no INT NOT NULL COMMENT 'Version number',
  title VARCHAR(128) NOT NULL COMMENT 'Title',
  content TEXT NOT NULL COMMENT 'Content',
  content_preview VARCHAR(256) NOT NULL COMMENT 'Content preview',
  version_status VARCHAR(32) NOT NULL COMMENT 'DRAFT / PENDING / ACTIVE / REJECTED',
  audit_status VARCHAR(32) NOT NULL COMMENT 'SKIPPED / PENDING / PASSED / REJECTED',
  audit_reason VARCHAR(256) NULL,
  created_by BIGINT NOT NULL COMMENT 'Creator user ID',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_version (note_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note version';

CREATE TABLE IF NOT EXISTS note_media (
  id BIGINT NOT NULL COMMENT 'Primary key',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  version_no INT NOT NULL COMMENT 'Version number',
  file_id BIGINT NOT NULL COMMENT 'File ID',
  media_type VARCHAR(32) NOT NULL COMMENT 'IMAGE / VIDEO',
  sort_order INT NOT NULL COMMENT 'Display order',
  is_cover TINYINT NOT NULL COMMENT 'Whether this media is cover',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_note_media_version (note_id, version_no, sort_order),
  KEY idx_note_media_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note media';

CREATE TABLE IF NOT EXISTS note_topic (
  id BIGINT NOT NULL COMMENT 'Primary key',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  version_no INT NOT NULL COMMENT 'Version number',
  topic_name VARCHAR(64) NOT NULL COMMENT 'Topic name',
  sort_order INT NOT NULL COMMENT 'Display order',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_topic (note_id, version_no, topic_name),
  KEY idx_note_topic_name (topic_name, note_id),
  KEY idx_note_topic_note (note_id, version_no, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note topic';

CREATE TABLE IF NOT EXISTS note_like (
  id BIGINT NOT NULL COMMENT 'Primary key',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Note author user ID',
  user_id BIGINT NOT NULL COMMENT 'Like user ID',
  like_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / CANCELED',
  liked_at DATETIME(3) NULL,
  canceled_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_like_user (note_id, user_id),
  KEY idx_note_like_note_time (note_id, like_status, liked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note like detail';

CREATE TABLE IF NOT EXISTS note_collection (
  id BIGINT NOT NULL COMMENT 'Primary key',
  note_id BIGINT NOT NULL COMMENT 'Note ID',
  author_id BIGINT NOT NULL COMMENT 'Note author user ID',
  user_id BIGINT NOT NULL COMMENT 'Collection user ID',
  collection_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / CANCELED',
  collected_at DATETIME(3) NULL,
  canceled_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_collection_user (note_id, user_id),
  KEY idx_note_collection_user_time (user_id, collection_status, collected_at, note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note collection detail';

CREATE TABLE IF NOT EXISTS note_idempotent_request (
  idempotent_key VARCHAR(128) NOT NULL COMMENT 'Idempotency key',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  operation VARCHAR(64) NOT NULL COMMENT 'Operation name',
  biz_id BIGINT NULL COMMENT 'Related business ID',
  request_hash VARCHAR(128) NOT NULL COMMENT 'Request hash',
  request_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING / SUCCESS / FAIL',
  response_payload JSON NULL COMMENT 'Successful response snapshot',
  expire_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (idempotent_key),
  KEY idx_note_idem_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note idempotent request';

CREATE TABLE IF NOT EXISTS note_outbox_event (
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'NotePublished / NoteUpdated / NoteDeleted / interaction events',
  aggregate_id BIGINT NOT NULL COMMENT 'Aggregate ID, normally noteId',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_note_outbox_status_retry (send_status, next_retry_at),
  KEY idx_note_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Note outbox event';
