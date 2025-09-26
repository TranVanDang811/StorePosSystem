package com.possystem.backend.category.service;

import com.possystem.backend.category.dto.CategoryRequest;
import com.possystem.backend.category.dto.CategoryResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    CategoryResponse getById(String categoryId);
    Page<CategoryResponse> getAll(int page, int size);
    void delete(String category);
}
