package com.possystem.backend.product.dto;

import com.possystem.backend.category.dto.CategoryResponse;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.supplier.dto.SupplierResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
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
    ProductStatus status;
    CategoryResponse category;
    SupplierResponse supplier;
    String imageUrl;
    LocalDateTime createdAt;
    LocalDateTime updateAt;
}
