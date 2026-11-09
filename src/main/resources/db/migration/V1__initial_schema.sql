-- =====================================================================
-- POS SYSTEM - BASELINE SCHEMA (V1)
-- Engine: MySQL 8.0 InnoDB (utf8mb4)
-- =====================================================================

CREATE TABLE IF NOT EXISTS role (
    name VARCHAR(50) NOT NULL PRIMARY KEY,
    description VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    username VARCHAR(255) NULL UNIQUE,
    password VARCHAR(255) NULL,
    full_name VARCHAR(255) NULL,
    email VARCHAR(255) NULL,
    phone VARCHAR(50) NULL,
    status VARCHAR(50) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_role (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_profile (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    loyalty_points INT NOT NULL DEFAULT 0,
    membership_level VARCHAR(50) NULL,
    user_id VARCHAR(36) NULL,
    CONSTRAINT fk_customer_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS employee_profile (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    position VARCHAR(100) NULL,
    salary DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    hire_date DATE NULL,
    shift VARCHAR(50) NULL,
    user_id VARCHAR(36) NULL,
    CONSTRAINT fk_employee_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS category (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS supplier (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NULL,
    email VARCHAR(255) NULL,
    address VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    product_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    cost DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    unit VARCHAR(50) NULL,
    stock INT NOT NULL DEFAULT 0,
    image_url VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NULL,
    supplier_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    CONSTRAINT fk_product_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    order_code VARCHAR(100) NOT NULL UNIQUE,
    total_price DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(15, 2) NULL DEFAULT 0.00,
    final_amount DECIMAL(15, 2) NULL DEFAULT 0.00,
    discount_code VARCHAR(100) NULL,
    status VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_detail (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    refunded_quantity INT NOT NULL DEFAULT 0,
    price DECIMAL(15, 2) NULL DEFAULT 0.00,
    total_price DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_order_detail_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_detail_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    user_id VARCHAR(36) NULL,
    order_id VARCHAR(36) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    point_discount DECIMAL(15, 2) NULL,
    earned_points INT NULL,
    used_points INT NULL DEFAULT 0,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_date DATETIME(6) NOT NULL,
    transaction_id VARCHAR(255) NULL,
    note VARCHAR(255) NULL,
    cash_received DECIMAL(15, 2) NULL,
    change_amount DECIMAL(15, 2) NULL,
    total_refunded DECIMAL(15, 2) NULL DEFAULT 0.00,
    refund_history_json TEXT NULL,
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS import_order (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    import_date DATETIME(6) NULL,
    import_update_date DATETIME(6) NULL,
    note VARCHAR(255) NULL,
    total_quantity INT NOT NULL DEFAULT 0,
    total_price DECIMAL(15, 2) NULL DEFAULT 0.00,
    status VARCHAR(50) NULL,
    confirm_status VARCHAR(50) NULL,
    supplier_id VARCHAR(36) NULL,
    CONSTRAINT fk_import_order_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS import_order_detail (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    quantity INT NOT NULL DEFAULT 0,
    received_quantity INT NOT NULL DEFAULT 0,
    confirmed_quantity INT NOT NULL DEFAULT 0,
    import_price DECIMAL(15, 2) NULL DEFAULT 0.00,
    import_order_id VARCHAR(36) NULL,
    product_id VARCHAR(36) NULL,
    CONSTRAINT fk_import_detail_order FOREIGN KEY (import_order_id) REFERENCES import_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_import_detail_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shift (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    shift_code VARCHAR(100) NULL UNIQUE,
    opened_by VARCHAR(36) NULL,
    closed_by VARCHAR(36) NULL,
    start_time DATETIME(6) NULL,
    end_time DATETIME(6) NULL,
    cash_sales DECIMAL(15, 2) NULL,
    cash_refunds DECIMAL(15, 2) NULL,
    opening_cash DECIMAL(15, 2) NULL,
    expected_cash DECIMAL(15, 2) NULL,
    counted_cash DECIMAL(15, 2) NULL,
    difference DECIMAL(15, 2) NULL,
    deposited_cash DECIMAL(15, 2) NULL,
    status VARCHAR(50) NULL,
    CONSTRAINT fk_shift_opened_by FOREIGN KEY (opened_by) REFERENCES users (id),
    CONSTRAINT fk_shift_closed_by FOREIGN KEY (closed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS discount (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NULL,
    code VARCHAR(100) NULL,
    description VARCHAR(255) NULL,
    value DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    active BIT(1) NOT NULL DEFAULT b'1',
    start_date DATETIME(6) NULL,
    end_date DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS invalidated_token (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    expiry_time DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

