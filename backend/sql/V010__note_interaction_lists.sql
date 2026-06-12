-- Add read indexes for profile interaction lists.
-- Contract source: docs/contracts/api/05-note-api.md

USE bluenote_note;

SET @index_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = 'bluenote_note'
    AND table_name = 'note_like'
    AND index_name = 'idx_note_like_user_time'
);

SET @add_index_sql := IF(
  @index_exists = 0,
  'ALTER TABLE bluenote_note.note_like ADD INDEX idx_note_like_user_time (user_id, like_status, liked_at, note_id)',
  'SELECT 1'
);

PREPARE add_index_stmt FROM @add_index_sql;
EXECUTE add_index_stmt;
DEALLOCATE PREPARE add_index_stmt;
