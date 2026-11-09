package com.possystem.backend.report.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductSalesReportResponse {

    String productCode;
    String productName;
    String categoryName;

    Long totalQuantity;
    BigDecimal totalRevenue;

    public ProductSalesReportResponse(String productCode, String productName, String categoryName, Number totalQuantity, Number totalRevenue) {
        this.productCode = productCode;
        this.productName = productName;
        this.categoryName = categoryName != null ? categoryName : "Chưa phân loại";
        this.totalQuantity = totalQuantity != null ? totalQuantity.longValue() : 0L;
        if (totalRevenue instanceof BigDecimal) {
            this.totalRevenue = (BigDecimal) totalRevenue;
        } else if (totalRevenue != null) {
            this.totalRevenue = BigDecimal.valueOf(totalRevenue.doubleValue());
        } else {
            this.totalRevenue = BigDecimal.ZERO;
        }
    }
}