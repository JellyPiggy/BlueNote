-- BlueNote IM schema for single chat foundation.
-- Contract source: docs/contracts/api/13-im-api.md

CREATE DATABASE IF NOT EXISTS bluenote_im
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE bluenote_im;

CREATE TABLE IF NOT EXISTS im_conversation (
  conversation_id BIGINT NOT NULL COMMENT 'Conversation ID',
  conversation_type VARCHAR(32) NOT NULL COMMENT 'SINGLE / GROUP',
  single_key VARCHAR(64) NULL COMMENT 'Single chat unique key: minUserId:maxUserId',
  current_seq BIGINT NOT NULL DEFAULT 0 COMMENT 'Current conversation message sequence',
  last_message_id BIGINT NULL COMMENT 'Last message ID',
  last_message_at DATETIME(3) NULL COMMENT 'Last message time',
  conversation_status VARCHAR(32) NOT NULL COMMENT 'NORMAL',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (conversation_id),
  UNIQUE KEY uk_im_single_key (single_key),
  KEY idx_im_conversation_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM conversation';

CREATE TABLE IF NOT EXISTS im_conversation_member (
  id BIGINT NOT NULL COMMENT 'Primary key',
  conversation_id BIGINT NOT NULL COMMENT 'Conversation ID',
  user_id BIGINT NOT NULL COMMENT 'Member user ID',
  peer_user_id BIGINT NULL COMMENT 'Single chat peer user ID',
  member_role VARCHAR(32) NOT NULL COMMENT 'OWNER / MEMBER',
  member_status VARCHAR(32) NOT NULL COMMENT 'NORMAL / BLOCKED / LEFT',
  last_read_seq BIGINT NOT NULL DEFAULT 0 COMMENT 'Last read conversation sequence',
  last_received_seq BIGINT NOT NULL DEFAULT 0 COMMENT 'Last received conversation sequence',
  unread_count INT NOT NULL DEFAULT 0 COMMENT 'Unread count in this conversation',
  pinned TINYINT NOT NULL DEFAULT 0 COMMENT 'Pinned by this user',
  mute TINYINT NOT NULL DEFAULT 0 COMMENT 'Muted by this user',
  hidden TINYINT NOT NULL DEFAULT 0 COMMENT 'Hidden by this user',
  last_visible_seq BIGINT NOT NULL DEFAULT 0 COMMENT 'Messages at or before this seq are hidden after delete',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_im_member (conversation_id, user_id),
  KEY idx_im_member_user_list (user_id, hidden, pinned, updated_at),
  KEY idx_im_member_peer (user_id, peer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM conversation member state';

CREATE TABLE IF NOT EXISTS im_message (
  message_id BIGINT NOT NULL COMMENT 'Message ID',
  conversation_id BIGINT NOT NULL COMMENT 'Conversation ID',
  conversation_seq BIGINT NOT NULL COMMENT 'Sequence in conversation',
  sender_id BIGINT NOT NULL COMMENT 'Sender user ID',
  receiver_id BIGINT NOT NULL COMMENT 'Receiver user ID for single chat',
  client_msg_id VARCHAR(128) NOT NULL COMMENT 'Sender scoped idempotency key',
  message_type VARCHAR(32) NOT NULL COMMENT 'TEXT / IMAGE / NOTE_CARD',
  content_json JSON NOT NULL COMMENT 'Message content',
  summary VARCHAR(256) NOT NULL COMMENT 'List summary',
  message_status VARCHAR(32) NOT NULL COMMENT 'NORMAL / RECALLED / DELETED',
  sent_at DATETIME(3) NOT NULL COMMENT 'Send time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (message_id),
  UNIQUE KEY uk_im_message_client (sender_id, client_msg_id),
  UNIQUE KEY uk_im_message_conversation_seq (conversation_id, conversation_seq),
  KEY idx_im_message_sender_time (sender_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM message fact';

CREATE TABLE IF NOT EXISTS im_conversation_message (
  id BIGINT NOT NULL COMMENT 'Primary key',
  conversation_id BIGINT NOT NULL COMMENT 'Conversation ID',
  conversation_seq BIGINT NOT NULL COMMENT 'Sequence in conversation',
  message_id BIGINT NOT NULL COMMENT 'Message ID',
  sender_id BIGINT NOT NULL COMMENT 'Sender user ID',
  sent_at DATETIME(3) NOT NULL COMMENT 'Send time',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_im_conversation_seq (conversation_id, conversation_seq),
  KEY idx_im_conversation_message_time (conversation_id, sent_at, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM conversation message chain';

CREATE TABLE IF NOT EXISTS im_user_sequence (
  user_id BIGINT NOT NULL COMMENT 'User ID',
  current_seq BIGINT NOT NULL DEFAULT 0 COMMENT 'Current user message sequence',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM user sequence';

CREATE TABLE IF NOT EXISTS im_user_message (
  id BIGINT NOT NULL COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  user_seq BIGINT NOT NULL COMMENT 'Sequence in user message chain',
  conversation_id BIGINT NOT NULL COMMENT 'Conversation ID',
  conversation_seq BIGINT NOT NULL COMMENT 'Sequence in conversation',
  message_id BIGINT NOT NULL COMMENT 'Message ID',
  sender_id BIGINT NOT NULL COMMENT 'Sender user ID',
  read_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 unread, 1 read',
  received_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 not received, 1 received',
  sent_at DATETIME(3) NOT NULL COMMENT 'Send time',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_im_user_seq (user_id, user_seq),
  UNIQUE KEY uk_im_user_conversation_message (user_id, conversation_id, message_id),
  KEY idx_im_user_conversation_seq (user_id, conversation_id, conversation_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM user message chain';

CREATE TABLE IF NOT EXISTS im_consume_record (
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
  UNIQUE KEY uk_im_consumer_event (consumer_group, event_id),
  KEY idx_im_consume_status (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM consume record';

CREATE TABLE IF NOT EXISTS im_outbox_event (
  event_id VARCHAR(128) NOT NULL COMMENT 'Event ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'IM event type',
  aggregate_id VARCHAR(128) NOT NULL COMMENT 'Aggregate ID',
  payload JSON NOT NULL COMMENT 'Event payload',
  send_status VARCHAR(32) NOT NULL COMMENT 'INIT / SENT / FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (event_id),
  KEY idx_im_outbox_status_retry (send_status, next_retry_at),
  KEY idx_im_outbox_aggregate (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM outbox event';
