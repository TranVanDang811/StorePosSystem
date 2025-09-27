package com.possystem.backend.product.dto;

import com.possystem.backend.common.enums.ProductStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRequest {
    String name;
    BigDecimal price;
    BigDecimal cost;
    String unit;
    BigDecimal stock;
    LocalDate expiryDate;
    ProductStatus status;
    String categoryName;
    String imageUrl;
}

