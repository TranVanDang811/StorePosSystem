-- =====================================================================
-- POS SYSTEM - EVENT-DRIVEN ARCHITECTURE, OUTBOX, DLQ & PROJECTIONS (V5)
-- Engine: MySQL 8.0 InnoDB
-- Objectives: Reliable Outbox Worker Leasing, Exponential Backoff,
--             Dead Letter Queue, Per-Consumer Idempotency & Async Jobs
-- =====================================================================

-- 1. HARDEN outbox_events TABLE FOR LEASING, BACKOFF, ORDERING & DLQ
SET @sql_status := (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE outbox_events MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT ''PENDING''', 'SELECT 1')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events'
);
PREPARE stmt FROM @sql_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Ensure error_message column exists consistently across V3 and JPA entity
SET @exist_err := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'error_message');
SET @sql_err := IF(@exist_err = 0, 'ALTER TABLE outbox_events ADD COLUMN error_message VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @sql_err;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'event_version');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN event_version VARCHAR(20) NOT NULL DEFAULT ''1.0''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'correlation_id');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN correlation_id VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'ordering_key');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN ordering_key VARCHAR(120) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'max_retries');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN max_retries INT NOT NULL DEFAULT 5', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'next_retry_at');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN next_retry_at DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'locked_at');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN locked_at DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'locked_by');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN locked_by VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'dead_lettered_at');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN dead_lettered_at DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'replayed_count');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN replayed_count INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'last_replayed_at');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN last_replayed_at DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'version');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Indexes for worker polling, lease crash recovery, and per-aggregate ordering
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND INDEX_NAME = 'idx_outbox_poll');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_outbox_poll ON outbox_events (status, next_retry_at, created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND INDEX_NAME = 'idx_outbox_lock');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_outbox_lock ON outbox_events (status, locked_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND INDEX_NAME = 'idx_outbox_ordering');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_outbox_ordering ON outbox_events (ordering_key, created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. IDEMPOTENT CONSUMER LEDGER
CREATE TABLE IF NOT EXISTS processed_events (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
    execution_ms BIGINT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_processed_event_consumer UNIQUE (event_id, consumer_name),
    INDEX idx_processed_event_type (event_type, processed_at),
    INDEX idx_processed_aggregate (aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. IMMUTABLE AUDIT EVENT LOG
CREATE TABLE IF NOT EXISTS audit_events (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(100) NULL,
    actor VARCHAR(100) NULL,
    summary VARCHAR(500) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_audit_event_id UNIQUE (event_id),
    INDEX idx_audit_aggregate (aggregate_type, aggregate_id, occurred_at),
    INDEX idx_audit_type_time (event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. ASYNCHRONOUS ARCHIVAL INVOICE RECORDS
CREATE TABLE IF NOT EXISTS invoice_records (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    invoice_number VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    order_code VARCHAR(64) NULL,
    payment_id VARCHAR(64) NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(32) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
    rendered_html LONGTEXT NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_invoice_payment UNIQUE (payment_id),
    CONSTRAINT uq_invoice_number UNIQUE (invoice_number),
    INDEX idx_invoice_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. ASYNCHRONOUS DAILY SALES ANALYTICS PROJECTION
CREATE TABLE IF NOT EXISTS daily_sales_projection (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    metric_date DATE NOT NULL,
    orders_created BIGINT NOT NULL DEFAULT 0,
    orders_completed BIGINT NOT NULL DEFAULT 0,
    orders_cancelled BIGINT NOT NULL DEFAULT 0,
    payments_completed BIGINT NOT NULL DEFAULT 0,
    payments_failed BIGINT NOT NULL DEFAULT 0,
    total_revenue DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_discount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_loyalty_points_earned BIGINT NOT NULL DEFAULT 0,
    vouchers_redeemed BIGINT NOT NULL DEFAULT 0,
    shifts_closed BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_daily_projection_date UNIQUE (metric_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
