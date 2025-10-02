package com.possystem.backend.product.service;

import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ProductService {
    ProductResponse create(ProductRequest request, MultipartFile file);
    Page<ProductResponse> getProducts(int page, int size);
    Page<ProductResponse> filterProducts(ProductFilterRequest request);
    ProductResponse getProductCode(String productCode);
    void deleteProduct(String productId);
    Map<String, Object> getProductStatistics();
    ProductResponse changerStatus(String productId, ProductStatus status);
}
