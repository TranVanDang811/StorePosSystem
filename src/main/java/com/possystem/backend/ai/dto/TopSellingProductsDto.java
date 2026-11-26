package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopSellingProductsDto {
    String periodDescription;
    List<ProductItem> products;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ProductItem {
        String productCode;
        String productName;
        String category;
        long quantitySold;
        BigDecimal totalRevenue;
    }
}
