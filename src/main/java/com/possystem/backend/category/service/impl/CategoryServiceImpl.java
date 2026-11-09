package com.possystem.backend.category.service.impl;

import com.possystem.backend.category.dto.CategoryRequest;
import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.category.dto.CategorySummaryResponse;
import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.repository.CategoryRepository;
import com.possystem.backend.category.service.CategoryService;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.CategoryMapper;
import com.possystem.backend.product.repository.ProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryServiceImpl implements CategoryService {
    CategoryRepository categoryRepository;
    CategoryMapper categoryMapper;
    ProductRepository productRepository;

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public CategoryResponse create(CategoryRequest request) {
        if (request.getName() != null) {
            String trimmedName = request.getName().trim();
            if (categoryRepository.findByName(trimmedName).isPresent()) {
                throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
            }
        }
        Category category = categoryMapper.toCategory(request);
        if (category.getName() != null) {
            category.setName(category.getName().trim());
        }
        category = categoryRepository.save(category);
        CategoryResponse response = categoryMapper.toCategoryResponse(category);
        response.setProductCount(0L);
        return response;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public CategoryResponse update(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (request.getName() != null) {
            String newName = request.getName().trim();
            if (!newName.equalsIgnoreCase(category.getName())) {
                Optional<Category> existing = categoryRepository.findByName(newName);
                if (existing.isPresent() && !existing.get().getId().equals(category.getId())) {
                    throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
                }
            }
            category.setName(newName);
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().trim());
        }

        Category saved = categoryRepository.save(category);
        CategoryResponse response = categoryMapper.toCategoryResponse(saved);
        response.setProductCount(productRepository.countByCategory_Id(saved.getId()));
        return response;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public CategoryResponse getById(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        CategoryResponse response = categoryMapper.toCategoryResponse(category);
        response.setProductCount(productRepository.countByCategory_Id(category.getId()));
        return response;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public Page<CategoryResponse> getAll(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Page<Category> categoryPage = categoryRepository.filterCategories(kw, pageable);

        java.util.Map<String, Long> productCountMap = productRepository.countProductsGroupedByCategory()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).longValue(),
                        (v1, v2) -> v1
                ));

        return categoryPage.map(cat -> {
            CategoryResponse res = categoryMapper.toCategoryResponse(cat);
            res.setProductCount(productCountMap.getOrDefault(cat.getId(), 0L));
            return res;
        });
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public CategorySummaryResponse getSummary() {
        long totalCategories = categoryRepository.count();
        long categoriesWithProducts = 0;
        long totalProducts = productRepository.count();

        var groupedCounts = productRepository.countProductsGroupedByCategory();
        if (groupedCounts != null) {
            categoriesWithProducts = groupedCounts.size();
        }
        long emptyCategories = Math.max(0, totalCategories - categoriesWithProducts);

        return CategorySummaryResponse.builder()
                .totalCategories(totalCategories)
                .categoriesWithProducts(categoriesWithProducts)
                .emptyCategories(emptyCategories)
                .totalProducts(totalProducts)
                .build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public void delete(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        long productCount = productRepository.countByCategory_Id(category.getId());
        if (productCount > 0) {
            throw new AppException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        categoryRepository.delete(category);
    }
}
