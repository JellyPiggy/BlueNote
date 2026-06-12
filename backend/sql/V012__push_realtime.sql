-- Push realtime channel schema delta.

USE bluenote_push;

ALTER TABLE push_delivery_attempt
  ADD COLUMN acked_at DATETIME(3) NULL COMMENT 'Realtime ack time' AFTER attempted_at;
