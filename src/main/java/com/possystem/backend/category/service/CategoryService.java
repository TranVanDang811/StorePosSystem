package com.possystem.backend.category.service;

import com.possystem.backend.category.dto.CategoryRequest;
import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.category.dto.CategorySummaryResponse;
import org.springframework.data.domain.Page;

public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(String id, CategoryRequest request);
    CategoryResponse getById(String categoryId);
    Page<CategoryResponse> getAll(int page, int size, String keyword);
    CategorySummaryResponse getSummary();
    void delete(String category);
}
