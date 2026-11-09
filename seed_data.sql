-- UTF-8 Support
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Insert Categories
INSERT INTO `category` (`id`, `name`, `description`, `created_at`, `updated_at`) VALUES
('cat-01', 'Cà phê & Trà', 'Các loại cà phê pha phin, cà phê máy và trà hoa quả thanh mát', NOW(), NOW()),
('cat-02', 'Bánh ngọt & Tráng miệng', 'Bánh mì nướng, bánh kem và các món tráng miệng phong phú', NOW(), NOW()),
('cat-03', 'Đồ uống đóng chai', 'Nước khoáng thiên nhiên, nước ngọt đóng lon và nước ép tươi', NOW(), NOW()),
('cat-04', 'Đồ ăn nhẹ', 'Khoai tây chiên giòn, xúc xích, snack khai vị', NOW(), NOW())
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `description`=VALUES(`description`), `updated_at`=NOW();

-- 2. Insert Suppliers
INSERT INTO `supplier` (`id`, `name`, `phone`, `email`, `address`, `created_at`, `updated_at`) VALUES
('sup-01', 'Công ty CP Cà phê Cầu Đất Farm', '02633889900', 'contact@caudatfarm.vn', 'Xã Trạm Hành, TP. Đà Lạt, Lâm Đồng', NOW(), NOW()),
('sup-02', 'Công ty Cổ phần Sữa Việt Nam (Vinamilk)', '02854155555', 'vinamilk@vinamilk.com.vn', '10 Tân Trào, P. Tân Phú, Quận 7, TP. HCM', NOW(), NOW()),
('sup-03', 'Công ty TNHH Bánh kẹo Á Châu (ABC Bakery)', '02837520859', 'info@abcbakery.co', '545 Kinh Dương Vương, P. An Lạc, Q. Bình Tân, TP. HCM', NOW(), NOW())
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `phone`=VALUES(`phone`), `email`=VALUES(`email`), `address`=VALUES(`address`), `updated_at`=NOW();

-- 3. Insert 10 Sample Products
INSERT INTO `product` (`id`, `product_code`, `name`, `price`, `cost`, `unit`, `stock`, `status`, `image_url`, `category_id`, `supplier_id`, `created_at`, `updated_at`) VALUES
('prod-001', 'SP001', 'Cà phê Phin Sữa Đá', 29000.00, 12000.00, 'Ly', 100, 'ACTIVE', 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=400', 'cat-01', 'sup-01', NOW(), NOW()),
('prod-002', 'SP002', 'Cà phê Đen Đá Đậm Vị', 25000.00, 9000.00, 'Ly', 85, 'ACTIVE', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400', 'cat-01', 'sup-01', NOW(), NOW()),
('prod-003', 'SP003', 'Bạc Xỉu Sài Gòn 3 Tầng', 32000.00, 14000.00, 'Ly', 90, 'ACTIVE', 'https://images.unsplash.com/photo-1517701604599-bb29b565090c?w=400', 'cat-01', 'sup-01', NOW(), NOW()),
('prod-004', 'SP004', 'Trà Đào Cam Sả Tươi', 39000.00, 16000.00, 'Ly', 70, 'ACTIVE', 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=400', 'cat-01', 'sup-01', NOW(), NOW()),
('prod-005', 'SP005', 'Trà Sữa Trân Châu Đường Đen', 45000.00, 19000.00, 'Ly', 110, 'ACTIVE', 'https://images.unsplash.com/photo-1558857563-b37cf533a693?w=400', 'cat-01', 'sup-02', NOW(), NOW()),
('prod-006', 'SP006', 'Bánh Croissant Bơ Tỏi Pháp', 35000.00, 15000.00, 'Cái', 40, 'ACTIVE', 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=400', 'cat-02', 'sup-03', NOW(), NOW()),
('prod-007', 'SP007', 'Bánh Tiramisu Mascarpone', 48000.00, 22000.00, 'Cái', 35, 'ACTIVE', 'https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=400', 'cat-02', 'sup-03', NOW(), NOW()),
('prod-008', 'SP008', 'Nước Suối Thiên Nhiên 500ml', 10000.00, 4000.00, 'Chai', 150, 'ACTIVE', 'https://images.unsplash.com/photo-1523362628745-0c100150b504?w=400', 'cat-03', 'sup-02', NOW(), NOW()),
('prod-009', 'SP009', 'Nước Ép Cam Tươi Nguyên Chất', 38000.00, 16000.00, 'Chai', 60, 'ACTIVE', 'https://images.unsplash.com/photo-1613478223719-2ab802602423?w=400', 'cat-03', 'sup-02', NOW(), NOW()),
('prod-010', 'SP010', 'Khoai Tây Chiên Phô Mai Lắc', 32000.00, 13000.00, 'Phần', 55, 'ACTIVE', 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=400', 'cat-04', 'sup-03', NOW(), NOW())
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `price`=VALUES(`price`), `cost`=VALUES(`cost`), `unit`=VALUES(`unit`), `stock`=VALUES(`stock`), `status`=VALUES(`status`), `image_url`=VALUES(`image_url`), `updated_at`=NOW();

-- 4. Update Admin User
UPDATE `user` 
SET `status` = 'ACTIVE', 
    `full_name` = 'Quản trị viên hệ thống', 
    `email` = 'admin@pos.vn', 
    `phone` = '0900000000',
    `updated_at` = NOW() 
WHERE `username` = 'admin';

-- 5. Insert 10 Sample Users (Password for all: Admin12345)
-- Bcrypt hash: $2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq
INSERT INTO `user` (`id`, `username`, `password`, `full_name`, `email`, `phone`, `status`, `created_at`, `updated_at`) VALUES
('usr-mng-01', 'manager01', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Trần Quang Vinh', 'vinh.tran@pos.vn', '0981111222', 'ACTIVE', NOW(), NOW()),
('usr-mng-02', 'manager02', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Lê Thị Ngọc Bích', 'bich.le@pos.vn', '0982222333', 'ACTIVE', NOW(), NOW()),
('usr-stf-01', 'staff01', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Nguyễn Văn An', 'an.nguyen@pos.vn', '0971234567', 'ACTIVE', NOW(), NOW()),
('usr-stf-02', 'staff02', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Phạm Thu Hà', 'ha.pham@pos.vn', '0972345678', 'ACTIVE', NOW(), NOW()),
('usr-stf-03', 'staff03', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Võ Minh Đức', 'duc.vo@pos.vn', '0973456789', 'ACTIVE', NOW(), NOW()),
('usr-cst-01', 'cust01', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Hoàng Anh Tuấn', 'tuan.hoang@gmail.com', '0909123456', 'ACTIVE', NOW(), NOW()),
('usr-cst-02', 'cust02', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Đỗ Phương Linh', 'linh.do@gmail.com', '0909234567', 'ACTIVE', NOW(), NOW()),
('usr-cst-03', 'cust03', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Vũ Hải Nam', 'nam.vu@gmail.com', '0909345678', 'ACTIVE', NOW(), NOW()),
('usr-cst-04', 'cust04', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Trịnh Mai Phương', 'phuong.trinh@gmail.com', '0909456789', 'ACTIVE', NOW(), NOW()),
('usr-cst-05', 'cust05', '$2a$10$lA4JFIdVriYPwszklQvBH.gDs.1DldoA1b.tWkj9F3LGXBklmwLeq', 'Bùi Quốc Huy', 'huy.bui@gmail.com', '0909567890', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE `full_name`=VALUES(`full_name`), `email`=VALUES(`email`), `phone`=VALUES(`phone`), `status`=VALUES(`status`), `updated_at`=NOW();

-- 6. Assign Roles (user_role)
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
('usr-mng-01', 'MANAGE'),
('usr-mng-02', 'MANAGE'),
('usr-stf-01', 'EMPLOYEE'),
('usr-stf-02', 'EMPLOYEE'),
('usr-stf-03', 'EMPLOYEE'),
('usr-cst-01', 'CUSTOMER'),
('usr-cst-02', 'CUSTOMER'),
('usr-cst-03', 'CUSTOMER'),
('usr-cst-04', 'CUSTOMER'),
('usr-cst-05', 'CUSTOMER')
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`);

-- 7. Insert Employee Profiles
INSERT INTO `employee_profile` (`id`, `user_id`, `position`, `salary`, `shift`, `hire_date`) VALUES
('ep-01', 'usr-mng-01', 'Cửa Hàng Trưởng', 18000000.0, 'Full-time', '2023-01-15'),
('ep-02', 'usr-mng-02', 'Phó Cửa Hàng Trưởng', 14000000.0, 'Full-time', '2023-03-01'),
('ep-03', 'usr-stf-01', 'Thu Ngân', 8500000.0, 'Ca Sáng (06:00 - 14:00)', '2023-06-10'),
('ep-04', 'usr-stf-02', 'Thu Ngân', 8500000.0, 'Ca Chiều (14:00 - 22:00)', '2023-08-20'),
('ep-05', 'usr-stf-03', 'Barista Pha Chế', 9000000.0, 'Xoay Ca Linh Hoạt', '2023-07-01')
ON DUPLICATE KEY UPDATE `position`=VALUES(`position`), `salary`=VALUES(`salary`), `shift`=VALUES(`shift`), `hire_date`=VALUES(`hire_date`);

-- 8. Insert Customer Profiles
INSERT INTO `customer_profile` (`id`, `user_id`, `loyalty_points`, `membership_level`) VALUES
('cp-01', 'usr-cst-01', 250, 'Vàng'),
('cp-02', 'usr-cst-02', 120, 'Bạc'),
('cp-03', 'usr-cst-03', 40, 'Đồng'),
('cp-04', 'usr-cst-04', 520, 'Kim Cương'),
('cp-05', 'usr-cst-05', 15, 'Đồng')
ON DUPLICATE KEY UPDATE `loyalty_points`=VALUES(`loyalty_points`), `membership_level`=VALUES(`membership_level`);

SET FOREIGN_KEY_CHECKS = 1;

