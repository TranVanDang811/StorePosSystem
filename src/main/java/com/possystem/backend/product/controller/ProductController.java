package com.possystem.backend.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.product.dto.ProductFilterRequest;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Product Management", description = "Product Management API")
public class ProductController {
    ProductService productService;

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Create product", description = "Add new products (with images). Transfer `product` as JSON and `images` as files.")
    public ApiResponse<ProductResponse> create(
            @RequestPart("product") String productJson,
            @RequestPart(value = "images", required = false) MultipartFile images) {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductRequest productRequest;
        try {
            productRequest = objectMapper.readValue(productJson, ProductRequest.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_JSON);
        }
        return ApiResponse.<ProductResponse>builder()
                .result(productService.create(productRequest, images))
                .build();
    }

    // Get a list of products with pagination
    @GetMapping
    @Operation(summary = "Get all products", description = "Get a paginated list of products")
    ApiResponse<Page<ProductResponse>> getProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<ProductResponse>>builder()
                .result(productService.getProducts(page - 1, size))
                .build();
    }

    // Filter products
    @GetMapping("/filter")
    @Operation(summary = "Filter products", description = "Filter products by multiple conditions (name, category, status, price...)")
    public ApiResponse<Page<ProductResponse>> filterProducts(ProductFilterRequest request) {
        return ApiResponse.<Page<ProductResponse>>builder()
                .result(productService.filterProducts(request))
                .build();
    }

    // Get product by code
    @GetMapping("/{productCode}")
    @Operation(summary = "Get product by code", description = "Get product details by product code (productCode)")
    ApiResponse<ProductResponse> getProductCode(@PathVariable String productCode) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.getProductCode(productCode))
                .build();
    }

    // Delete product
    @DeleteMapping("/{productId}")
    @Operation(summary = "Delete product", description = "Delete product by ID")
    ApiResponse<Void>deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(productId);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }

    // Statistics
    @GetMapping("/statistics")
    @Operation(summary = "Product statistics", description = "Product statistics (quantity, stock, status, etc.)")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.<Map<String, Object>>builder()
                .result(productService.getProductStatistics())
                .build();
    }

    // Change product status
    @PatchMapping("/{productId}")
    @Operation(summary = "Change product status", description = "Update product status (ACTIVE, OUT_OF_STOCK, etc.)")
    public ApiResponse<ProductResponse> changerStatus(
            @PathVariable String productId,
            @RequestParam ProductStatus status) {
        ProductResponse response = productService.changerStatus(productId, status);

        return ApiResponse.<ProductResponse>builder()
                .result(response)
                .build();
    }
}
