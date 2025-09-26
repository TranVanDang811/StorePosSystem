package com.possystem.backend.common.util.mapper;


import com.possystem.backend.category.entity.Category;
import com.possystem.backend.category.dto.CategoryRequest;
import com.possystem.backend.category.dto.CategoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    Category toCategory(CategoryRequest request);

    @Mapping(target = "id", source = "id")
    CategoryResponse toCategoryResponse(Category category);
}
