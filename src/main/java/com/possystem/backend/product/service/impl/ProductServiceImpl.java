package com.possystem.backend.product.service.impl;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.CloudinaryUploadResult;
import com.possystem.backend.common.service.CloudinaryService;
import com.possystem.backend.common.util.mapper.ProductMapper;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.service.ProductService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {

    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    CloudinaryService cloudinaryService;
    ProductMapper productMapper;

    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(ProductRequest request, MultipartFile file) {
        Product product = productMapper.toProduct(request);

        Category category = categoryRepository.findByName(request.getCategoryName())
                .orElseThrow(() -> new RuntimeException("Category not found"));
        product.setCategory(category);

        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }

        CloudinaryUploadResult result = cloudinaryService.uploadImage(file);
        if (result.getUrl() == null || result.getUrl().isEmpty()) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }

      
        product.setImageUrl(result.getUrl());

        product = productRepository.save(product);

        log.info("Saved product: {}", product);

        return productMapper.toProductResponse(product);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<ProductResponse> getProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findAll(pageable)
                .map(productMapper::toProductResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE')")
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

        int pageIndex = Math.max(0, request.getPage() - 1);

        Pageable pageable = orders.isEmpty()
                ? PageRequest.of(pageIndex, request.getSize())
                : PageRequest.of(pageIndex, request.getSize(), Sort.by(orders));

        ProductStatus status = null;
        if (request.getStatus() != null) {
            try {
                status = ProductStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_STATUS);
            }
        }

        Page<Product> productPage = productRepository.filterProducts(
                request.getKeyword(),
                request.getProductCode(),
                request.getCategoryName(),
                request.getPrice(),
                status,
                pageable
        );

        return productPage.map(productMapper::toProductResponse);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public ProductResponse getProductCode(String productCode) {
        return productMapper.toProductResponse(
                productRepository.findByProductCode(productCode).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        productRepository.delete(product);
    }


    public Map<String, Object> getProductStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalProducts", productRepository.count());
        stats.put("activeProducts", productRepository.countByStatus(ProductStatus.ACTIVE));
        stats.put("inactiveProducts", productRepository.countByStatus(ProductStatus.OUT_OF_STOCK));
        stats.put("pendingProducts", productRepository.countByStatus(ProductStatus.DISCONTINUED));

        return stats;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse changerStatus(String productId, ProductStatus status) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        product.setStatus(status);

        return productMapper.toProductResponse(productRepository.save(product));
    }
}
