-- BlueNote auth schema for the first main chain.
-- Contract source: docs/contracts/db/01-main-chain-schema.md

CREATE DATABASE IF NOT EXISTS bluenote_auth
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_auth;

CREATE TABLE IF NOT EXISTS auth_account (
  user_id BIGINT NOT NULL COMMENT 'User ID',
  username VARCHAR(32) NOT NULL COMMENT 'Login username',
  account_status VARCHAR(32) NOT NULL COMMENT 'NORMAL / DISABLED / BANNED / DELETED',
  register_channel VARCHAR(32) NOT NULL COMMENT 'APP / H5',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_auth_account_username (username),
  KEY idx_auth_account_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Auth account';

CREATE TABLE IF NOT EXISTS auth_password (
  id BIGINT NOT NULL COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  password_hash VARCHAR(255) NOT NULL COMMENT 'Password hash',
  password_algo VARCHAR(32) NOT NULL COMMENT 'BCrypt / Argon2id',
  password_version INT NOT NULL COMMENT 'Password version',
  password_updated_at DATETIME(3) NOT NULL,
  need_reset TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_password_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Password credential';

CREATE TABLE IF NOT EXISTS auth_session (
  session_id BIGINT NOT NULL COMMENT 'Session ID',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  device_id VARCHAR(128) NOT NULL COMMENT 'Device ID',
  device_name VARCHAR(128) NULL COMMENT 'Device name',
  platform VARCHAR(32) NOT NULL COMMENT 'IOS / ANDROID / H5',
  app_version VARCHAR(32) NOT NULL COMMENT 'App version',
  refresh_token_hash VARCHAR(128) NOT NULL COMMENT 'Refresh token hash',
  refresh_token_expires_at DATETIME(3) NOT NULL,
  session_status VARCHAR(32) NOT NULL COMMENT 'ACTIVE / LOGGED_OUT / EXPIRED / REVOKED',
  login_ip VARCHAR(64) NULL,
  last_active_at DATETIME(3) NULL,
  revoked_at DATETIME(3) NULL,
  revoke_reason VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (session_id),
  UNIQUE KEY uk_auth_session_refresh_hash (refresh_token_hash),
  KEY idx_auth_session_user_status (user_id, session_status),
  KEY idx_auth_session_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Device session';

CREATE TABLE IF NOT EXISTS auth_login_audit (
  id BIGINT NOT NULL COMMENT 'Primary key',
  user_id BIGINT NULL COMMENT 'User ID',
  username VARCHAR(32) NULL COMMENT 'Login username',
  action VARCHAR(32) NOT NULL COMMENT 'REGISTER / LOGIN / LOGOUT / REFRESH / CHANGE_PASSWORD',
  result VARCHAR(32) NOT NULL COMMENT 'SUCCESS / FAIL',
  fail_reason VARCHAR(128) NULL,
  ip VARCHAR(64) NULL,
  device_id VARCHAR(128) NULL,
  platform VARCHAR(32) NULL,
  app_version VARCHAR(32) NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_auth_audit_user_time (user_id, created_at),
  KEY idx_auth_audit_username_time (username, created_at),
  KEY idx_auth_audit_ip_time (ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Login audit';

CREATE TABLE IF NOT EXISTS auth_outbox_event (
  id BIGINT NOT NULL COMMENT 'Primary key',
  event_id VARCHAR(64) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'Event type',
  aggregate_id BIGINT NOT NULL COMMENT 'Aggregate ID, normally userId',
  payload JSON NOT NULL COMMENT 'Event payload',
  status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_outbox_event (event_id),
  KEY idx_auth_outbox_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Auth outbox event';
