-- BlueNote file schema for the first main chain.
-- Contract source: docs/contracts/db/01-main-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_file
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_file;

CREATE TABLE IF NOT EXISTS file_object (
  file_id BIGINT NOT NULL COMMENT 'File ID',
  owner_id BIGINT NOT NULL COMMENT 'Upload owner user ID',
  scene VARCHAR(32) NOT NULL COMMENT 'USER_AVATAR / USER_HOME_COVER / NOTE_IMAGE / NOTE_VIDEO',
  storage_type VARCHAR(32) NOT NULL COMMENT 'MINIO',
  bucket VARCHAR(128) NOT NULL COMMENT 'Bucket name',
  object_key VARCHAR(512) NOT NULL COMMENT 'Object key',
  original_filename VARCHAR(255) NULL COMMENT 'Original filename',
  file_ext VARCHAR(16) NOT NULL COMMENT 'File extension',
  mime_type VARCHAR(128) NOT NULL COMMENT 'MIME type',
  file_size BIGINT NOT NULL COMMENT 'File size in bytes',
  etag VARCHAR(128) NULL COMMENT 'Object storage ETag',
  width INT NULL COMMENT 'Image width',
  height INT NULL COMMENT 'Image height',
  duration_ms BIGINT NULL COMMENT 'Video duration',
  file_status VARCHAR(32) NOT NULL COMMENT 'INIT / UPLOADED / BOUND / DELETED / BLOCKED',
  audit_status VARCHAR(32) NOT NULL COMMENT 'SKIPPED / PENDING / PASSED / REJECTED',
  access_level VARCHAR(32) NOT NULL COMMENT 'PUBLIC / PRIVATE',
  uploaded_at DATETIME(3) NULL,
  bound_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (file_id),
  UNIQUE KEY uk_file_object_key (bucket, object_key),
  KEY idx_file_owner_scene (owner_id, scene, created_at),
  KEY idx_file_status_time (file_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='File metadata';

CREATE TABLE IF NOT EXISTS file_upload_session (
  upload_id BIGINT NOT NULL COMMENT 'Upload session ID',
  file_id BIGINT NOT NULL COMMENT 'File ID',
  owner_id BIGINT NOT NULL COMMENT 'Upload owner user ID',
  upload_method VARCHAR(32) NOT NULL COMMENT 'PRESIGNED_PUT',
  upload_status VARCHAR(32) NOT NULL COMMENT 'INIT / UPLOADED / EXPIRED / FAILED',
  expected_size BIGINT NOT NULL COMMENT 'Expected file size',
  expected_mime_type VARCHAR(128) NOT NULL COMMENT 'Expected MIME type',
  upload_url_expire_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (upload_id),
  KEY idx_upload_file (file_id),
  KEY idx_upload_status_expire (upload_status, upload_url_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='File upload session';

CREATE TABLE IF NOT EXISTS file_binding (
  id BIGINT NOT NULL COMMENT 'Primary key',
  file_id BIGINT NOT NULL COMMENT 'File ID',
  owner_id BIGINT NOT NULL COMMENT 'File owner user ID',
  bind_type VARCHAR(64) NOT NULL COMMENT 'USER_AVATAR / USER_HOME_COVER / NOTE_MEDIA',
  bind_id VARCHAR(128) NOT NULL COMMENT 'Business object ID',
  bind_status VARCHAR(32) NOT NULL COMMENT 'BOUND / UNBOUND',
  bind_version BIGINT NOT NULL COMMENT 'Bind version',
  bound_at DATETIME(3) NULL,
  unbound_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_binding (file_id, bind_type, bind_id),
  KEY idx_file_binding_file (file_id, bind_status),
  KEY idx_file_binding_biz (bind_type, bind_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='File business binding';

CREATE TABLE IF NOT EXISTS file_outbox_event (
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'FileUploaded / FileBound / FileDeleted',
  aggregate_id BIGINT NOT NULL COMMENT 'Aggregate ID, normally fileId',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_file_outbox_status_retry (send_status, next_retry_at),
  KEY idx_file_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='File outbox event';
