-- =====================================================================
-- POS SYSTEM - DATABASE & TRANSACTION RELIABILITY UPGRADE (V4)
-- Engine: MySQL 8.0 InnoDB
-- Objectives: Idempotency, Stock Movement Audit, Loyalty History, Voucher Usage
-- =====================================================================

-- 1. PAYMENT IDEMPOTENCY KEY COLUMN
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment' AND COLUMN_NAME = 'idempotency_key');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE payment ADD COLUMN idempotency_key VARCHAR(100) NULL UNIQUE', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. DISCOUNT USAGE LIMIT COLUMNS
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount' AND COLUMN_NAME = 'usage_limit');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE discount ADD COLUMN usage_limit INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discount' AND COLUMN_NAME = 'used_count');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE discount ADD COLUMN used_count INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. STOCK MOVEMENT / INVENTORY AUDIT TRAIL TABLE
CREATE TABLE IF NOT EXISTS stock_movement (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    product_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100) NULL,
    quantity_before INT NOT NULL,
    quantity_change INT NOT NULL,
    quantity_after INT NOT NULL,
    note VARCHAR(255) NULL,
    created_by VARCHAR(36) NULL,
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES product (id),
    INDEX idx_stock_movement_prod_time (product_id, created_at),
    INDEX idx_stock_movement_ref (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. LOYALTY TRANSACTION HISTORY & IDEMPOTENCY TABLE
CREATE TABLE IF NOT EXISTS loyalty_transaction (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    customer_profile_id VARCHAR(36) NOT NULL,
    reference_id VARCHAR(100) NULL,
    transaction_type VARCHAR(50) NOT NULL,
    points_change INT NOT NULL,
    balance_after INT NOT NULL,
    idempotency_key VARCHAR(100) NULL UNIQUE,
    note VARCHAR(255) NULL,
    CONSTRAINT fk_loyalty_tx_profile FOREIGN KEY (customer_profile_id) REFERENCES customer_profile (id) ON DELETE CASCADE,
    INDEX idx_loyalty_tx_profile (customer_profile_id, created_at),
    INDEX idx_loyalty_tx_ref (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. DISCOUNT USAGE TRACKING & DUPLICATE REDEMPTION GUARD
CREATE TABLE IF NOT EXISTS discount_usage (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    discount_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    user_phone VARCHAR(50) NULL,
    discount_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    used_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_discount_usage_discount FOREIGN KEY (discount_id) REFERENCES discount (id),
    CONSTRAINT fk_discount_usage_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uq_discount_order UNIQUE (discount_id, order_id),
    INDEX idx_discount_usage_phone (user_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
