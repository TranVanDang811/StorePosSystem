package com.possystem.backend.category.controller;

import com.possystem.backend.category.dto.CategoryRequest;
import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.category.service.CategoryService;
import com.possystem.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Category Management", description = "Product Catalog Management API")
public class CategoryController {

    CategoryService categoryService;

    //Created category
    @PostMapping
    @Operation(summary = "Create category", description = "Add a new category")
    ApiResponse<CategoryResponse> create(@RequestBody CategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.create(request))
                .build();
    }

    //Get category by id
    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Get category details by ID")
    ApiResponse<CategoryResponse> getById(@PathVariable String id) {
        return ApiResponse.<CategoryResponse>builder().result(categoryService.getById(id)).build();
    }


    //Get all category
    @GetMapping
    @Operation(summary = "Get all categories", description = "Get a list of paginated categories")
    ApiResponse<Page<CategoryResponse>> getAll(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<CategoryResponse>>builder()
                .result(categoryService.getAll(page - 1, size))
                .build();
    }

    //Delete category
    @DeleteMapping("/{category}")
    @Operation(summary = "Delete category", description = "Delete category by name or id (depending on business rule)")
    ApiResponse<Void> delete(@PathVariable String category) {
        categoryService.delete(category);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }
}
