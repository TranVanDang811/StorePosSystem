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
public class ProfitabilityReportDto {
    List<ProfitItem> topProfitableProducts;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ProfitItem {
        String productCode;
        String name;
        String category;
        BigDecimal sellingPrice;
        BigDecimal costPrice;
        BigDecimal unitProfit; // price - cost
        double profitMarginPercentage; // ((price - cost) / price) * 100
    }
}
