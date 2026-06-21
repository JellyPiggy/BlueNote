-- Add persisted hot score for comment HOT sorting.
-- Contract source: docs/contracts/db/02-social-chain-schema.md

USE bluenote_comment;

SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = 'bluenote_comment'
    AND table_name = 'content_comment'
    AND column_name = 'hot_score_snapshot'
);

SET @add_column_sql := IF(
  @column_exists = 0,
  'ALTER TABLE bluenote_comment.content_comment ADD COLUMN hot_score_snapshot BIGINT NOT NULL DEFAULT 0 COMMENT ''reply_count_snapshot * 6 + like_count_snapshot * 4'' AFTER reply_count_snapshot',
  'SELECT 1'
);

PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;

UPDATE bluenote_comment.content_comment
SET hot_score_snapshot = reply_count_snapshot * 6 + like_count_snapshot * 4
WHERE level = 1
  AND hot_score_snapshot <> reply_count_snapshot * 6 + like_count_snapshot * 4;

SET @index_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = 'bluenote_comment'
    AND table_name = 'content_comment'
    AND index_name = 'idx_comment_note_root_hot'
);

SET @add_index_sql := IF(
  @index_exists = 0,
  'ALTER TABLE bluenote_comment.content_comment ADD INDEX idx_comment_note_root_hot (note_id, level, comment_status, hot_score_snapshot, created_at, comment_id)',
  'SELECT 1'
);

PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;
