package com.possystem.backend.category.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategorySummaryResponse {
    long totalCategories;
    long categoriesWithProducts;
    long emptyCategories;
    long totalProducts;
}

