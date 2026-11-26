-- =====================================================================
-- POS SYSTEM - PRODUCTION OBSERVABILITY & DISTRIBUTED TRACING (V6)
-- Engine: MySQL 8.0 InnoDB
-- Objectives: Persist W3C traceparent context across Transactional Outbox
--             so background workers & Kafka consumers continue traces.
-- =====================================================================

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND COLUMN_NAME = 'trace_parent');
SET @sql := IF(@exist = 0, 'ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(128) NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outbox_events' AND INDEX_NAME = 'idx_outbox_trace_parent');
SET @sql_idx := IF(@exist_idx = 0, 'CREATE INDEX idx_outbox_trace_parent ON outbox_events (trace_parent)', 'SELECT 1');
PREPARE stmt FROM @sql_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
