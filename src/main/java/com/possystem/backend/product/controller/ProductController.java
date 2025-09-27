package com.possystem.backend.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductController {
    ProductService productService;

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
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
    ApiResponse<Page<ProductResponse>> getProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<ProductResponse>>builder()
                .result(productService.getProducts(page - 1, size))
                .build();
    }
}
