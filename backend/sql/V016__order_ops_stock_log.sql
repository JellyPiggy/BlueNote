USE bluenote_order;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bluenote_order'
      AND TABLE_NAME = 'coupon_stock_log'
      AND COLUMN_NAME = 'operator_type'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE coupon_stock_log ADD COLUMN operator_type VARCHAR(32) NULL AFTER change_amount',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bluenote_order'
      AND TABLE_NAME = 'coupon_stock_log'
      AND COLUMN_NAME = 'operator_id'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE coupon_stock_log ADD COLUMN operator_id VARCHAR(64) NULL AFTER operator_type',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bluenote_order'
      AND TABLE_NAME = 'coupon_stock_log'
      AND COLUMN_NAME = 'reason'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE coupon_stock_log ADD COLUMN reason VARCHAR(256) NULL AFTER operator_id',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
