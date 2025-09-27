package com.possystem.backend.product.service.impl;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.CloudinaryUploadResult;
import com.possystem.backend.common.service.CloudinaryService;
import com.possystem.backend.common.util.mapper.ProductMapper;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.entity.ProductImage;
import com.possystem.backend.product.repository.ProductImageRepository;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.product.service.ProductService;
import com.possystem.backend.user.dto.UserResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.HashSet;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {

    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    ProductImageRepository imageRepository;
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
}
