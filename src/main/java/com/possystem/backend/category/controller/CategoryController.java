package com.possystem.backend.category.controller;

import com.possystem.backend.category.dto.CategoryRequest;
import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.category.dto.CategorySummaryResponse;
import com.possystem.backend.category.service.CategoryService;
import com.possystem.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Category Management", description = "Product Catalog Management API")
public class CategoryController {

    CategoryService categoryService;

    // Create category
    @PostMapping
    @Operation(summary = "Create category", description = "Add a new category")
    ApiResponse<CategoryResponse> create(@RequestBody @Valid CategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.create(request))
                .build();
    }

    // Update category
    @PutMapping("/{id}")
    @Operation(summary = "Update category", description = "Update category details by ID")
    ApiResponse<CategoryResponse> update(@PathVariable String id, @RequestBody @Valid CategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.update(id, request))
                .build();
    }

    // Get category summary KPIs
    @GetMapping("/summary")
    @Operation(summary = "Get category summary", description = "Get category overview and KPIs")
    ApiResponse<CategorySummaryResponse> getSummary() {
        return ApiResponse.<CategorySummaryResponse>builder()
                .result(categoryService.getSummary())
                .build();
    }

    // Get category by id
    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Get category details by ID")
    ApiResponse<CategoryResponse> getById(@PathVariable String id) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.getById(id))
                .build();
    }

    // Get all categories with optional search
    @GetMapping
    @Operation(summary = "Get all categories", description = "Get a list of paginated categories")
    ApiResponse<Page<CategoryResponse>> getAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.<Page<CategoryResponse>>builder()
                .result(categoryService.getAll(page - 1, size, keyword))
                .build();
    }

    // Delete category
    @DeleteMapping("/{category}")
    @Operation(summary = "Delete category", description = "Delete category by id")
    ApiResponse<Void> delete(@PathVariable String category) {
        categoryService.delete(category);
        return ApiResponse.<Void>builder().message("Xóa danh mục thành công").build();
    }
}
