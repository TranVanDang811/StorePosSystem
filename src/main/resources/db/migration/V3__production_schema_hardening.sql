-- =====================================================================
-- POS SYSTEM - PRODUCTION SCHEMA HARDENING & CONCURRENCY UPGRADE (V3)
-- Engine: MySQL 8.0 InnoDB
-- =====================================================================

-- 1. TRANSACTIONAL OUTBOX EVENTS TABLE (Guaranteed Event Delivery)
CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    last_error TEXT,
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6),
    INDEX idx_outbox_status_created (status, created_at),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. WEBHOOK IDEMPOTENCY & AUDIT TABLE
CREATE TABLE IF NOT EXISTS webhook_events (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(191) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6),
    INDEX idx_webhook_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. OPTIMISTIC LOCKING VERSION COLUMNS
-- Orders version column
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'version');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- CustomerProfile version column
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer_profile' AND COLUMN_NAME = 'version');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE customer_profile ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. USER TOKEN INVALIDATION TRACKING
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'password_changed_at');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE user ADD COLUMN password_changed_at DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. SHIFT MULTI-TERMINAL SUPPORT
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shift' AND COLUMN_NAME = 'terminal_id');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE shift ADD COLUMN terminal_id VARCHAR(50) NOT NULL DEFAULT ''MAIN_COUNTER''', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. SHIFT MULTI-TERMINAL INDEX
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shift' AND INDEX_NAME = 'idx_shift_status_terminal');
SET @sqlstmt := IF(@exist = 0, 'CREATE INDEX idx_shift_status_terminal ON shift (status, terminal_id)', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
