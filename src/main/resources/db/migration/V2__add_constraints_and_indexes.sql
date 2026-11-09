-- =====================================================================
-- POS SYSTEM - PRODUCTION CONSTRAINTS & PERFORMANCE INDEXES (V2)
-- Engine: MySQL 8.0 InnoDB
-- =====================================================================

-- 1. BỔ SUNG UNIQUE CONSTRAINTS BẢO VỆ TÍNH TOÀN VẸN
ALTER TABLE discount 
    MODIFY COLUMN code VARCHAR(100) NOT NULL,
    ADD CONSTRAINT uq_discount_code UNIQUE (code);

ALTER TABLE payment 
    ADD CONSTRAINT uq_payment_order_id UNIQUE (order_id);

ALTER TABLE customer_profile 
    ADD CONSTRAINT uq_customer_profile_user_id UNIQUE (user_id);

ALTER TABLE employee_profile 
    ADD CONSTRAINT uq_employee_profile_user_id UNIQUE (user_id);

ALTER TABLE order_detail 
    ADD CONSTRAINT uq_order_detail_order_product UNIQUE (order_id, product_id);

ALTER TABLE category 
    ADD CONSTRAINT uq_category_name UNIQUE (name);

ALTER TABLE supplier 
    ADD CONSTRAINT uq_supplier_name UNIQUE (name);

-- 2. BỔ SUNG INDEXES TỐI ƯU HÓA TRUY VẤN
-- Bảng orders
CREATE INDEX idx_orders_created_at ON orders (created_at);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);

-- Bảng payment
CREATE INDEX idx_payment_date ON payment (payment_date);
CREATE INDEX idx_payment_status_date ON payment (status, payment_date);
CREATE INDEX idx_payment_method_status ON payment (method, status, payment_date);
CREATE INDEX idx_payment_user_id ON payment (user_id);

-- Bảng product
CREATE INDEX idx_product_category_status ON product (category_id, status);
CREATE INDEX idx_product_name ON product (name);

-- Bảng invalidated_token
CREATE INDEX idx_invalidated_token_expiry ON invalidated_token (expiry_time);

-- Bảng import_order
CREATE INDEX idx_import_order_date_status ON import_order (import_date, status);
CREATE INDEX idx_import_order_supplier ON import_order (supplier_id);

