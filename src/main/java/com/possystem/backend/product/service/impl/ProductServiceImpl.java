package com.possystem.backend.product.service.impl;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.common.cache.CacheInvalidationService;
import com.possystem.backend.common.cache.CacheNames;
import com.possystem.backend.common.cache.CachedPage;
import com.possystem.backend.common.cache.PosCacheService;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.common.response.CloudinaryUploadResult;
import com.possystem.backend.common.service.CloudinaryService;
import com.possystem.backend.common.util.mapper.ProductMapper;
import com.possystem.backend.importorder.repository.ImportOrderDetailRepository;
import com.possystem.backend.order.repository.OrderDetailRepository;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.entity.StockMovement;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.repository.StockMovementRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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
    PosCacheService posCacheService;
    CacheInvalidationService cacheInvalidationService;
    StockMovementRepository stockMovementRepository;
    OutboxPublisher outboxPublisher;
    PosBusinessMetrics businessMetrics;

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

        if (request.getStock() != null) {
            product.setStock(request.getStock().intValue());
        }

        if (product.getStock() <= 0) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        } else if (product.getStatus() == null) {
            product.setStatus(ProductStatus.ACTIVE);
        }

        Category category = categoryRepository.findByName(request.getCategoryName())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found: " + request.getCategoryName()));
        product.setCategory(category);

        Supplier supplier = supplierRepository.findByName(request.getSupplierName())
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND, "Supplier not found: " + request.getSupplierName()));
        product.setSupplier(supplier);

        if (file != null && !file.isEmpty()) {
            CloudinaryUploadResult result = cloudinaryService.uploadImage(file);
            product.setImageUrl(result.getUrl());
        }

        product = productRepository.save(product);
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictProductCaches();
        }
        return productMapper.toProductResponse(product);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public ProductResponse update(String productId, ProductRequest request, MultipartFile file) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

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

        int beforeStock = product.getStock();
        boolean stockAdjusted = false;
        if (request.getStock() != null) {
            int newStock = request.getStock().intValue();
            stockAdjusted = (newStock != beforeStock);
            product.setStock(newStock);
            if (newStock <= 0 && product.getStatus() == ProductStatus.ACTIVE) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            } else if (newStock > 0 && product.getStatus() == ProductStatus.OUT_OF_STOCK) {
                product.setStatus(ProductStatus.ACTIVE);
            }
        }

        if (request.getCategoryName() != null && !request.getCategoryName().isBlank()) {
            Category category = categoryRepository.findByName(request.getCategoryName().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found: " + request.getCategoryName()));
            product.setCategory(category);
        }

        if (request.getSupplierName() != null && !request.getSupplierName().isBlank()) {
            Supplier supplier = supplierRepository.findByName(request.getSupplierName().trim())
                    .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND, "Supplier not found: " + request.getSupplierName()));
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
        if (stockAdjusted) {
            int delta = product.getStock() - beforeStock;
            if (stockMovementRepository != null) {
                stockMovementRepository.save(StockMovement.builder()
                        .product(product)
                        .movementType("ADJUSTMENT")
                        .referenceId(product.getId())
                        .quantityBefore(beforeStock)
                        .quantityChange(delta)
                        .quantityAfter(product.getStock())
                        .note("Cập nhật tồn kho sản phẩm " + product.getProductCode())
                        .build());
            }
            if (outboxPublisher != null) {
                Map<String, Object> invPayload = new LinkedHashMap<>();
                invPayload.put("productId", product.getId());
                invPayload.put("productCode", product.getProductCode());
                invPayload.put("productName", product.getName());
                invPayload.put("movementType", "ADJUSTMENT");
                invPayload.put("beforeStock", beforeStock);
                invPayload.put("quantityChange", delta);
                invPayload.put("afterStock", product.getStock());
                invPayload.put("referenceId", product.getId());
                outboxPublisher.publishInCurrentTransaction(
                        "INVENTORY",
                        product.getId(),
                        PosEventType.INVENTORY_UPDATED,
                        "product:" + product.getId(),
                        product.getId(),
                        invPayload);
            }
            if (businessMetrics != null) {
                businessMetrics.recordInventoryAdjustment("ADJUSTMENT", delta);
            }
        }
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictProductCaches();
        }
        return productMapper.toProductResponse(product);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public Page<ProductResponse> getProducts(int page, int size) {
        String key = "raw:p=" + page + ":s=" + size;
        if (posCacheService != null) {
            CachedPage<ProductResponse> cached = posCacheService.getOrLoad(
                    CacheNames.PRODUCTS_LIST,
                    key,
                    () -> {
                        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
                        return CachedPage.from(productRepository.findAll(pageable).map(productMapper::toProductResponse));
                    });
            return cached != null ? cached.toPage() : Page.empty();
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return productRepository.findAll(pageable).map(productMapper::toProductResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public Page<ProductResponse> filterProducts(ProductFilterRequest request) {
        int pageIndex = Math.max(0, request.getPage() - 1);
        int pageSize = request.getSize() > 0 ? request.getSize() : 10;

        String keyword = trimToNull(request.getKeyword());
        String productCode = trimToNull(request.getProductCode());
        String categoryName = trimToNull(request.getCategoryName());
        ProductStatus status = parseStatus(request.getStatus());
        BigDecimal price = parsePrice(request.getPrice());

        String cacheKey = buildFilterCacheKey(
                pageIndex, pageSize, keyword, productCode, categoryName, price, status,
                request.getSortByPrice(), request.getSortByName(), request.getSortByCreatedAt()
        );

        if (posCacheService != null) {
            CachedPage<ProductResponse> cached = posCacheService.getOrLoad(
                    CacheNames.PRODUCTS_LIST,
                    cacheKey,
                    () -> CachedPage.from(loadFilteredProductsFromDb(
                            request, pageIndex, pageSize, keyword, productCode, categoryName, price, status
                    )));
            return cached != null ? cached.toPage() : Page.empty();
        }

        return loadFilteredProductsFromDb(request, pageIndex, pageSize, keyword, productCode, categoryName, price, status);
    }

    private Page<ProductResponse> loadFilteredProductsFromDb(
            ProductFilterRequest request,
            int pageIndex,
            int pageSize,
            String keyword,
            String productCode,
            String categoryName,
            BigDecimal price,
            ProductStatus status
    ) {
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

        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(orders));

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
        String key = "code:" + code.toUpperCase();
        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.PRODUCTS_DETAIL, key, () ->
                    productMapper.toProductResponse(
                            productRepository.findByProductCodeIgnoreCase(code)
                                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND))));
        }
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
            product.setStatus(ProductStatus.DISCONTINUED);
            productRepository.save(product);
        } else {
            productRepository.delete(product);
        }

        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictProductCaches();
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Map<String, Object> getProductStatistics() {
        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.PRODUCTS_STATS, "summary", this::loadProductStatsFromDb);
        }
        return loadProductStatsFromDb();
    }

    private Map<String, Object> loadProductStatsFromDb() {
        Map<String, Object> stats = new LinkedHashMap<>();
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

        if (product.getStatus() == ProductStatus.DISCONTINUED && status != ProductStatus.DISCONTINUED) {
            throw new AppException(ErrorCode.PRODUCT_DISCONTINUED);
        }

        if (status == ProductStatus.ACTIVE && product.getStock() <= 0) {
            throw new AppException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        product.setStatus(status);
        ProductResponse response = productMapper.toProductResponse(productRepository.save(product));
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictProductCaches();
        }
        return response;
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE')")
    public List<ProductResponse> getProductsBySupplier(String supplierId) {
        if (posCacheService != null) {
            return posCacheService.getOrLoad(
                    CacheNames.PRODUCTS_BY_SUPPLIER,
                    supplierId,
                    () -> new ArrayList<>(productRepository.findBySupplier_Id(supplierId).stream()
                            .map(productMapper::toProductResponse)
                            .toList()));
        }
        return new ArrayList<>(productRepository.findBySupplier_Id(supplierId).stream()
                .map(productMapper::toProductResponse)
                .toList());
    }

    private ProductStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return ProductStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
    }

    private BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawPrice.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private String buildFilterCacheKey(
            int pageIndex, int pageSize, String keyword, String productCode,
            String categoryName, BigDecimal price, ProductStatus status,
            String sortByPrice, String sortByName, String sortByCreatedAt) {
        return String.format("p=%d:s=%d:kw=%s:code=%s:cat=%s:price=%s:st=%s:sp=%s:sn=%s:sc=%s",
                pageIndex,
                pageSize,
                keyword != null ? keyword.toLowerCase() : "-",
                productCode != null ? productCode.toLowerCase() : "-",
                categoryName != null ? categoryName.toLowerCase() : "-",
                price != null ? price.stripTrailingZeros().toPlainString() : "-",
                status != null ? status.name() : "-",
                sortByPrice != null ? sortByPrice.trim().toUpperCase() : "-",
                sortByName != null ? sortByName.trim().toUpperCase() : "-",
                sortByCreatedAt != null ? sortByCreatedAt.trim().toUpperCase() : "-");
    }
}
