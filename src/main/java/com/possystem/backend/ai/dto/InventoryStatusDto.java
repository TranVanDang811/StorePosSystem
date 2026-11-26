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
public class InventoryStatusDto {
    long totalOutOfStockCount;
    long totalLowStockCount;
    int thresholdUsed;
    List<StockItem> outOfStockProducts;
    List<StockItem> lowStockProducts;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class StockItem {
        String productCode;
        String name;
        int currentStock;
        String unit;
        BigDecimal price;
    }
}
