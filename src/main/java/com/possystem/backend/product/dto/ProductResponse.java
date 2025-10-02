package com.possystem.backend.product.dto;

import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.common.enums.ProductStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductResponse {
    String id;
    String productCode;
    String name;
    BigDecimal price;
    BigDecimal cost;
    String unit;
    BigDecimal stock;
    LocalDate expiryDate;
    ProductStatus status;
    CategoryResponse category;
    String imageUrl;
    LocalDateTime createdAt;
    LocalDateTime updateAt;
}
