package com.possystem.backend.product.service.impl;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.CloudinaryUploadResult;
import com.possystem.backend.common.service.CloudinaryService;
import com.possystem.backend.common.util.mapper.ProductMapper;
import com.possystem.backend.importorder.repository.ImportOrderDetailRepository;
import com.possystem.backend.order.repository.OrderDetailRepository;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.service.ProductService;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {

    CategoryRepository categoryRepository;
    SupplierRepository supplierRepository;
    ProductRepository productRepository;
    CloudinaryService cloudinaryService;
    ProductMapper productMapper;
    OrderDetailRepository orderDetailRepository;
    ImportOrderDetailRepository importOrderDetailRepository;

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ProductResponse create(ProductRequest request, MultipartFile file) {
        if (request.getProductCode() != null && !request.getProductCode().isBlank()) {
            String code = request.getProductCode().trim();
            if (productRepository.existsByProductCodeIgnoreCase(code)) {
                throw new AppException(ErrorCode.PRODUCT_CODE_EXISTED);
            }
        }

        Product product = productMapper.toProduct(request);

        // Chuẩn hóa tồn kho và trạng thái ban đầu
        if (request.getStock() != null) {
            product.setStock(request.getStock().intValue());
        }

        if (product.getStock() <= 0) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        } else if (product.getStatus() == null) {
            product.setStatus(ProductStatus.ACTIVE);
        }

        Category category = categoryRepository.findByName(request.getCategoryName())
                .orElseThrow(() -> new RuntimeException("Category not found: " + request.getCategoryName()));
        product.setCategory(category);

        Supplier supplier = supplierRepository.findByName(request.getSupplierName())
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + request.getSupplierName()));
        product.setSupplier(supplier);

        if (file != null && !file.isEmpty()) {
            CloudinaryUploadResult result = cloudinaryService.uploadImage(file);
            product.setImageUrl(result.getUrl());
        }

        product = productRepository.save(product);
        return productMapper.toProductResponse(product);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ProductResponse update(String productId, ProductRequest request, MultipartFile file) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Kiểm tra trùng mã sản phẩm nếu thay đổi mã
        if (request.getProductCode() != null && !request.getProductCode().isBlank()) {
            String newCode = request.getProductCode().trim();
            if (!newCode.equalsIgnoreCase(product.getProductCode())
                    && productRepository.existsByProductCodeIgnoreCase(newCode)) {
                throw new AppException(ErrorCode.PRODUCT_CODE_EXISTED);
            }
            product.setProductCode(newCode);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName().trim());
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        if (request.getCost() != null) {
            product.setCost(request.getCost());
        }

        if (request.getUnit() != null && !request.getUnit().isBlank()) {
            product.setUnit(request.getUnit().trim());
        }

        if (request.getStock() != null) {
            int newStock = request.getStock().intValue();
            product.setStock(newStock);
            if (newStock <= 0 && product.getStatus() == ProductStatus.ACTIVE) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            } else if (newStock > 0 && product.getStatus() == ProductStatus.OUT_OF_STOCK) {
                product.setStatus(ProductStatus.ACTIVE);
            }
        }

        if (request.getCategoryName() != null && !request.getCategoryName().isBlank()) {
            Category category = categoryRepository.findByName(request.getCategoryName().trim())
                    .orElseThrow(() -> new RuntimeException("Category not found: " + request.getCategoryName()));
            product.setCategory(category);
        }

        if (request.getSupplierName() != null && !request.getSupplierName().isBlank()) {
            Supplier supplier = supplierRepository.findByName(request.getSupplierName().trim())
                    .orElseThrow(() -> new RuntimeException("Supplier not found: " + request.getSupplierName()));
            product.setSupplier(supplier);
        }

        if (request.getStatus() != null) {
            if (request.getStatus() == ProductStatus.ACTIVE && product.getStock() <= 0) {
                throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK);
            }
            product.setStatus(request.getStatus());
        }

        if (file != null && !file.isEmpty()) {
            CloudinaryUploadResult result = cloudinaryService.uploadImage(file);
            product.setImageUrl(result.getUrl());
        }

        product = productRepository.save(product);
        return productMapper.toProductResponse(product);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public Page<ProductResponse> getProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return productRepository.findAll(pageable)
                .map(productMapper::toProductResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public Page<ProductResponse> filterProducts(ProductFilterRequest request) {
        List<Sort.Order> orders = new ArrayList<>();

        Optional.ofNullable(request.getSortByPrice())
                .map(String::toUpperCase)
                .ifPresent(sort -> orders.add("ASC".equals(sort) ? Sort.Order.asc("price") : Sort.Order.desc("price")));

        Optional.ofNullable(request.getSortByName())
                .map(String::toUpperCase)
                .ifPresent(sort -> orders.add("ASC".equals(sort) ? Sort.Order.asc("name") : Sort.Order.desc("name")));

        Optional.ofNullable(request.getSortByCreatedAt())
                .map(String::toUpperCase)
                .ifPresent(sort -> orders.add("ASC".equals(sort) ? Sort.Order.asc("createdAt") : Sort.Order.desc("createdAt")));

        if (orders.isEmpty()) {
            orders.add(Sort.Order.desc("createdAt"));
        }

        int pageIndex = Math.max(0, request.getPage() - 1);
        int pageSize = request.getSize() > 0 ? request.getSize() : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(orders));

        ProductStatus status = null;
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                status = ProductStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_STATUS);
            }
        }

        String keyword = request.getKeyword();
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isBlank()) {
                keyword = null;
            }
        }

        String productCode = request.getProductCode();
        if (productCode != null) {
            productCode = productCode.trim();
            if (productCode.isBlank()) {
                productCode = null;
            }
        }

        String categoryName = request.getCategoryName();
        if (categoryName != null) {
            categoryName = categoryName.trim();
            if (categoryName.isBlank()) {
                categoryName = null;
            }
        }

        BigDecimal price = null;
        if (request.getPrice() != null && !request.getPrice().isBlank()) {
            try {
                price = new BigDecimal(request.getPrice().trim());
            } catch (Exception ignored) {}
        }

        Page<Product> productPage = productRepository.filterProducts(
                keyword,
                productCode,
                categoryName,
                price,
                status,
                pageable
        );

        return productPage.map(productMapper::toProductResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public ProductResponse getProductCode(String productCode) {
        String code = productCode != null ? productCode.trim() : "";
        return productMapper.toProductResponse(
                productRepository.findByProductCodeIgnoreCase(code)
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND)));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public void deleteProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        boolean hasOrderHistory = orderDetailRepository.existsByProductId(productId);
        boolean hasImportHistory = importOrderDetailRepository.existsByProductId(productId);

        if (hasOrderHistory || hasImportHistory) {
            // Đã có lịch sử bán hàng hoặc nhập kho -> Ngừng kinh doanh để bảo toàn toàn vẹn dữ liệu
            product.setStatus(ProductStatus.DISCONTINUED);
            productRepository.save(product);
            return;
        }

        productRepository.delete(product);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Map<String, Object> getProductStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalProducts", productRepository.count());
        stats.put("activeProducts", productRepository.countByStatus(ProductStatus.ACTIVE));
        stats.put("inactiveProducts", productRepository.countByStatus(ProductStatus.OUT_OF_STOCK));
        stats.put("pendingProducts", productRepository.countByStatus(ProductStatus.DISCONTINUED));

        return stats;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ProductResponse changerStatus(String productId, ProductStatus status) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // ❌ Không cho thay đổi nếu đã DISCONTINUED
        if (product.getStatus() == ProductStatus.DISCONTINUED && status != ProductStatus.DISCONTINUED) {
            throw new AppException(ErrorCode.PRODUCT_DISCONTINUED);
        }

        // ❌ Không cho ACTIVE nếu stock = 0
        if (status == ProductStatus.ACTIVE && product.getStock() <= 0) {
            throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        product.setStatus(status);
        return productMapper.toProductResponse(productRepository.save(product));
    }

    public List<ProductResponse> getProductsBySupplier(String supplierId) {
        return productRepository.findBySupplier_Id(supplierId)
                .stream()
                .map(productMapper::toProductResponse)
                .toList();
    }
}
