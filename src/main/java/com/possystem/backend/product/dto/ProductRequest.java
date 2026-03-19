package com.possystem.backend.product.dto;

import com.possystem.backend.common.enums.ProductStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRequest {
    String productCode;
    String name;
    BigDecimal price;
    BigDecimal cost;
    String unit;
    BigDecimal stock;
    ProductStatus status;
    String categoryName;
    String supplierName;
    String imageUrl;
}

