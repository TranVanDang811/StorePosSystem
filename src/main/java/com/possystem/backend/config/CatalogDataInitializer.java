package com.possystem.backend.config;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.common.cache.CacheInvalidationService;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CatalogDataInitializer {

        @Bean
        @Order(2)
        ApplicationRunner catalogInitRunner(
                        CategoryRepository categoryRepository,
                        SupplierRepository supplierRepository,
                        ProductRepository productRepository,
                        PlatformTransactionManager transactionManager,
                        org.springframework.beans.factory.ObjectProvider<CacheInvalidationService> cacheInvalidationServiceProvider) {

                return args -> {
                        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                        txTemplate.executeWithoutResult(status -> {
                                log.info("☕ Đang kiểm tra và khởi tạo dữ liệu danh mục, nhà cung cấp & sản phẩm Đẳng Cấp Coffee...");

                                // 1. Khởi tạo 6 danh mục chính
                                Map<String, Category> categoryMap = new HashMap<>();
                                seedCategory(categoryRepository, categoryMap, "Cà Phê",
                                                "Cà phê pha phin truyền thống và máy espresso chuẩn gu người Việt");
                                seedCategory(categoryRepository, categoryMap, "Trà & Trái Cây",
                                                "Trà thảo mộc, trà hoa quả tươi mát thanh nhiệt mát lạnh");
                                seedCategory(categoryRepository, categoryMap, "Trà Sữa",
                                                "Trà sữa đậm vị kem béo thơm ngon cùng đa dạng topping trân châu thạch");
                                seedCategory(categoryRepository, categoryMap, "Đá Xay & Sinh Tố",
                                                "Thức uống đá xay mát lạnh tuyết bông, sinh tố trái cây bổ dưỡng");
                                seedCategory(categoryRepository, categoryMap, "Bánh & Đồ Ăn Nhẹ",
                                                "Bánh ngọt châu Âu, bánh mì que giòn rụm và đồ ăn vặt nhâm nhi");
                                seedCategory(categoryRepository, categoryMap, "INGREDIENT",
                                                "Nguyên liệu pha chế thô, hạt cà phê, sữa, siro và vật tư tiêu hao");

                                // 2. Khởi tạo 5 nhà cung cấp uy tín
                                Map<String, Supplier> supplierMap = new HashMap<>();
                                seedSupplier(supplierRepository, supplierMap, "NPP Cà Phê Cao Nguyên Buôn Ma Thuột",
                                                "0901234567", "contact@caphecaonguyen.vn", "Buôn Ma Thuột, Đắk Lắk");
                                seedSupplier(supplierRepository, supplierMap, "Công Ty Cổ Phần Sữa Dalat Milk",
                                                "02633848888", "sales@dalatmilk.vn", "Đơn Dương, Lâm Đồng");
                                seedSupplier(supplierRepository, supplierMap,
                                                "Công Ty TNHH Siro & Topping Monin Việt Nam", "02839998888",
                                                "order@monin.vn", "Quận 1, TP. Hồ Chí Minh");
                                seedSupplier(supplierRepository, supplierMap,
                                                "Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh", "02862903738",
                                                "info@tannhathuong.com", "Tân Bình, TP. Hồ Chí Minh");
                                seedSupplier(supplierRepository, supplierMap, "Công Ty Bao Bì & Vật Tư Quán POS Xanh",
                                                "0918765432", "posxanh@baobi.vn", "Cầu Giấy, Hà Nội");

                                // 3. Khởi tạo 40 sản phẩm chuẩn Đẳng Cấp Coffee (Đồ uống, Thức ăn & Nguyên
                                // liệu)
                                int createdCount = 0;

                                // 3.1 Cà Phê (10 món)
                                createdCount += seedProduct(productRepository, "CF-DEN-DA", "Cà Phê Đen Đá Phin",
                                                "25000", "6500", "ly", 150,
                                                "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "CF-SUA-DA", "Cà Phê Sữa Đá Đẳng Cấp",
                                                "29000", "8000", "ly", 200,
                                                "https://images.unsplash.com/photo-1517701550927-30cf4ba1dba5?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "CF-BAC-XIU", "Bạc Xỉu 3 Tầng Sữa Đá",
                                                "32000", "9000", "ly", 120,
                                                "https://images.unsplash.com/photo-1572442388796-11668a67e53d?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "CF-MUOI-HUE", "Cà Phê Muối Cố Đô",
                                                "35000", "9500", "ly", 180,
                                                "https://images.unsplash.com/photo-1541167760496-1628856ab772?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "CF-TRUNG-HN", "Cà Phê Trứng Hà Nội",
                                                "39000", "11000", "ly", 60,
                                                "https://images.unsplash.com/photo-1509785307050-d4066910ec1e?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "CF-ESPRESSO", "Espresso Ý Single Shot",
                                                "30000", "7000", "ly", 100,
                                                "https://images.unsplash.com/photo-1510591509098-f4fdc6d0ff04?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "CF-AMERICANO", "Americano Đá Thanh Mát",
                                                "35000", "7500", "ly", 110,
                                                "https://images.unsplash.com/photo-1551030173-122aabc4489c?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "CF-LATTE", "Caffe Latte Nghệ Thuật",
                                                "45000", "12000", "ly", 90,
                                                "https://images.unsplash.com/photo-1534778101976-62847782c213?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                createdCount += seedProduct(productRepository, "CF-CAPPUCCINO",
                                                "Cappuccino Rắc Bột Quế", "45000", "12000", "ly", 85,
                                                "https://images.unsplash.com/photo-1577968897966-3d4325b36b61?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                createdCount += seedProduct(productRepository, "CF-COLDBREW-CAM",
                                                "Cold Brew Cam Vàng Sả Tươi", "48000", "13500", "ly", 70,
                                                "https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Cà Phê"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                // 3.2 Trà & Trái Cây (5 món)
                                createdCount += seedProduct(productRepository, "TRA-DAO-CAMSA",
                                                "Trà Đào Cam Sả Thượng Hạng", "38000", "9000", "ly", 160,
                                                "https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà & Trái Cây"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "TRA-VAI-HONG",
                                                "Trà Vải Hoa Hồng Quý Tộc", "42000", "10500", "ly", 130,
                                                "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà & Trái Cây"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "TRA-SEN-MACCHIATO",
                                                "Trà Sen Vàng Macchiato", "45000", "11500", "ly", 140,
                                                "https://images.unsplash.com/photo-1544787219-7f47ccb76574?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà & Trái Cây"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                createdCount += seedProduct(productRepository, "TRA-OOLONG-DAU",
                                                "Trà Oolong Dâu Tây Tươi", "45000", "11000", "ly", 95,
                                                "https://images.unsplash.com/photo-1621263764928-df1444c5e859?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà & Trái Cây"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "TRA-OI-HONG", "Trà Ổi Hồng Chanh Leo",
                                                "39000", "9200", "ly", 110,
                                                "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà & Trái Cây"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                // 3.3 Trà Sữa (4 món)
                                createdCount += seedProduct(productRepository, "TS-DUONG-DEN",
                                                "Sữa Tươi Trân Châu Đường Đen", "42000", "10000", "ly", 220,
                                                "https://images.unsplash.com/photo-1558857563-b37cf5a9161a?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà Sữa"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                createdCount += seedProduct(productRepository, "TS-OOLONG-NUONG",
                                                "Trà Sữa Oolong Nướng Kem Cheese", "45000", "11500", "ly", 175,
                                                "https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà Sữa"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "TS-LAI-THACH-DUA",
                                                "Trà Sữa Lài Thạch Dừa Giòn", "39000", "9000", "ly", 130,
                                                "https://images.unsplash.com/photo-1541658016709-82535e94bc69?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà Sữa"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                createdCount += seedProduct(productRepository, "TS-MATCHA-UJI",
                                                "Trà Sữa Matcha Uji Nhật Bản", "48000", "12800", "ly", 90,
                                                "https://images.unsplash.com/photo-1536256263959-770b48d82b0a?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Trà Sữa"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                // 3.4 Đá Xay & Sinh Tố (3 món)
                                createdCount += seedProduct(productRepository, "DX-COCO-COFFEE",
                                                "Cà Phê Cốt Dừa Đá Xay", "49000", "13000", "ly", 85,
                                                "https://images.unsplash.com/photo-1572490122747-3968b75cc699?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Đá Xay & Sinh Tố"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "DX-MATCHA-FREEZE",
                                                "Matcha Freeze Bánh Oreo Kem Tươi", "52000", "14000", "ly", 70,
                                                "https://images.unsplash.com/photo-1589733955941-5eeaf752f6dd?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Đá Xay & Sinh Tố"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                createdCount += seedProduct(productRepository, "ST-BO-SAP", "Sinh Tố Bơ Sáp Dừa Non",
                                                "55000", "15000", "ly", 65,
                                                "https://images.unsplash.com/photo-1553530666-ba11a7da3888?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Đá Xay & Sinh Tố"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                // 3.5 Bánh & Đồ Ăn Nhẹ (6 món)
                                createdCount += seedProduct(productRepository, "BA-CROISSANT",
                                                "Bánh Sừng Bò Bơ Pháp (Croissant)", "32000", "11000", "cái", 40,
                                                "https://images.unsplash.com/photo-1555507036-ab1f4038808a?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Bánh & Đồ Ăn Nhẹ"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                createdCount += seedProduct(productRepository, "BA-TIRAMISU", "Tiramisu Cà Phê Ý",
                                                "42000", "14000", "cái", 35,
                                                "https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Bánh & Đồ Ăn Nhẹ"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                createdCount += seedProduct(productRepository, "BA-MOUSSE-CHANHLEO",
                                                "Bánh Mousse Chanh Leo Hoàng Gia", "39000", "12000", "cái", 30,
                                                "https://images.unsplash.com/photo-1587314168485-3236d6710814?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Bánh & Đồ Ăn Nhẹ"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                createdCount += seedProduct(productRepository, "BA-BANHMI-QUE",
                                                "Bánh Mì Que Hải Phòng Pate Phô Mai", "20000", "6500", "cái", 50,
                                                "https://images.unsplash.com/photo-1509722747041-616f39b57569?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Bánh & Đồ Ăn Nhẹ"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                createdCount += seedProduct(productRepository, "AN-KHO-GA", "Khô Gà Lá Chanh Xé Cay",
                                                "28000", "10000", "đĩa", 80,
                                                "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Bánh & Đồ Ăn Nhẹ"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                createdCount += seedProduct(productRepository, "AN-HUONG-DUONG",
                                                "Hạt Hướng Dương Ngũ Vị", "15000", "5000", "đĩa", 120,
                                                "https://images.unsplash.com/photo-1543168256-418811576931?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("Bánh & Đồ Ăn Nhẹ"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                // 3.6 Nguyên Liệu Pha Chế & Tiêu Hao (12 món - giá bán 0 VND dành cho nội bộ)
                                createdCount += seedProduct(productRepository, "NL-ROBUSTA-BMT",
                                                "Cà Phê Hạt Robusta Buôn Ma Thuột (kg)", "0", "180000", "kg", 100,
                                                "https://images.unsplash.com/photo-1587734195503-904fca47e0e9?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "NL-ARABICA-CAUDAT",
                                                "Cà Phê Hạt Arabica Cầu Đất (kg)", "0", "280000", "kg", 80,
                                                "https://images.unsplash.com/photo-1559056199-641a0ac8b55e?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("NPP Cà Phê Cao Nguyên Buôn Ma Thuột"));

                                createdCount += seedProduct(productRepository, "NL-SUA-DALATMILK",
                                                "Sữa Tươi Thanh Trùng Dalat Milk 1L", "0", "38000", "hộp", 240,
                                                "https://images.unsplash.com/photo-1550583724-b2692b85b150?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                createdCount += seedProduct(productRepository, "NL-SUA-DAC-NSPN",
                                                "Sữa Đặc Ngôi Sao Phương Nam Xanh", "0", "22000", "lon", 300,
                                                "https://images.unsplash.com/photo-1528750997573-59b89d56f4f7?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty Cổ Phần Sữa Dalat Milk"));

                                createdCount += seedProduct(productRepository, "NL-TRA-OOLONG-BL",
                                                "Trà Oolong Thượng Hạng Bảo Lộc (kg)", "0", "250000", "kg", 50,
                                                "https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "NL-MATCHA-UJI-500G",
                                                "Bột Matcha Uji Kyoto 500g", "0", "650000", "gói", 25,
                                                "https://images.unsplash.com/photo-1536256263959-770b48d82b0a?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "NL-SIRO-DAO-MONIN",
                                                "Siro Đào Monin Chai Thủy Tinh 700ml", "0", "215000", "chai", 60,
                                                "https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "NL-DAO-KRONOS",
                                                "Đào Ngâm Đường Kronos Hộp 820g", "0", "48000", "hộp", 150,
                                                "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "NL-KEM-RICHS",
                                                "Kem Béo Thực Vật Richs Lùn 454g", "0", "32000", "hộp", 180,
                                                "https://images.unsplash.com/photo-1550583724-b2692b85b150?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Tân Nhất Hương Bakery & Nguyên Liệu Làm Bánh"));

                                createdCount += seedProduct(productRepository, "NL-TRAN-CHAU-DEN",
                                                "Hạt Trân Châu Đen Đài Loan Gói 3kg", "0", "110000", "bịch", 70,
                                                "https://images.unsplash.com/photo-1558857563-b37cf5a9161a?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty TNHH Siro & Topping Monin Việt Nam"));

                                createdCount += seedProduct(productRepository, "NL-LY-GIAY-500ML",
                                                "Ly Giấy 2 Lớp Chịu Nhiệt 500ml", "0", "65000", "cây", 200,
                                                "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty Bao Bì & Vật Tư Quán POS Xanh"));

                                createdCount += seedProduct(productRepository, "NL-ONG-HUT-BAMIA",
                                                "Ống Hút Bã Mía Tự Hủy Sinh Học", "0", "35000", "bịch", 250,
                                                "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?auto=format&fit=crop&w=600&q=80",
                                                categoryMap.get("INGREDIENT"),
                                                supplierMap.get("Công Ty Bao Bì & Vật Tư Quán POS Xanh"));

                                log.info("✅ Hoàn tất khởi tạo danh mục & sản phẩm: {} sản phẩm mới được bổ sung/cập nhật.",
                                                createdCount);

                                CacheInvalidationService cacheService = cacheInvalidationServiceProvider
                                                .getIfAvailable();
                                if (cacheService != null) {
                                        cacheService.evictProductCaches();
                                        cacheService.evictCategoryCaches();
                                        log.info("🧹 Đã làm mới Redis Cache cho danh mục & sản phẩm.");
                                }
                        });
                };
        }

        private void seedCategory(CategoryRepository repo, Map<String, Category> map, String name, String desc) {
                Category cat = repo.findByName(name).orElseGet(() -> repo.save(Category.builder()
                                .name(name)
                                .description(desc)
                                .build()));
                map.put(name, cat);
        }

        private void seedSupplier(SupplierRepository repo, Map<String, Supplier> map, String name, String phone,
                        String email, String address) {
                Supplier sup = repo.findByName(name).orElseGet(() -> repo.save(Supplier.builder()
                                .name(name)
                                .phone(phone)
                                .email(email)
                                .address(address)
                                .build()));
                map.put(name, sup);
        }

        private int seedProduct(ProductRepository repo, String code, String name, String price, String cost,
                        String unit,
                        int stock, String imageUrl, Category category, Supplier supplier) {
                if (repo.existsByProductCodeIgnoreCase(code)) {
                        // Cập nhật lại ảnh, giá, tồn kho nếu sản phẩm đã có nhưng chưa có ảnh hoặc
                        // thông tin mới
                        var existing = repo.findByProductCode(code);
                        if (existing.isPresent()) {
                                Product p = existing.get();
                                boolean changed = false;
                                if (p.getImageUrl() == null || p.getImageUrl().isBlank()) {
                                        p.setImageUrl(imageUrl);
                                        changed = true;
                                }
                                if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) == 0
                                                && !code.startsWith("NL-")) {
                                        p.setPrice(new BigDecimal(price));
                                        changed = true;
                                }
                                if (p.getCost() == null || p.getCost().compareTo(BigDecimal.ZERO) == 0) {
                                        p.setCost(new BigDecimal(cost));
                                        changed = true;
                                }
                                if (p.getUnit() == null || p.getUnit().isBlank()) {
                                        p.setUnit(unit);
                                        changed = true;
                                }
                                if (p.getStock() <= 0) {
                                        p.setStock(stock);
                                        p.setStatus(ProductStatus.ACTIVE);
                                        changed = true;
                                }
                                if (changed) {
                                        repo.save(p);
                                        return 1;
                                }
                        }
                        return 0;
                }

                Product product = Product.builder()
                                .productCode(code)
                                .name(name)
                                .price(new BigDecimal(price))
                                .cost(new BigDecimal(cost))
                                .unit(unit)
                                .stock(stock)
                                .imageUrl(imageUrl)
                                .status(stock > 0 ? ProductStatus.ACTIVE : ProductStatus.OUT_OF_STOCK)
                                .category(category)
                                .supplier(supplier)
                                .build();

                repo.save(product);
                return 1;
        }
}
