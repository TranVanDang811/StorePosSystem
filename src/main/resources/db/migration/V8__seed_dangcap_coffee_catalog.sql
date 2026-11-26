-- =====================================================================
-- POS SYSTEM - CATALOG & VERSION DEFAULTS SAFEGUARD (V8)
-- Ensures version column exists with NOT NULL DEFAULT 0 on all entities
-- =====================================================================

-- 1. PRODUCT VERSION COLUMN
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'version');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE product ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'ALTER TABLE product MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE product SET version = 0 WHERE version IS NULL;

-- 2. ORDERS VERSION COLUMN
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'version');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'ALTER TABLE orders MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE orders SET version = 0 WHERE version IS NULL;

-- 3. CUSTOMER_PROFILE VERSION COLUMN
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer_profile' AND COLUMN_NAME = 'version');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE customer_profile ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'ALTER TABLE customer_profile MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE customer_profile SET version = 0 WHERE version IS NULL;

-- 4. OUTBOX_EVENTS VERSION COLUMN
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'version');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'ALTER TABLE outbox_events MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE outbox_events SET version = 0 WHERE version IS NULL;
