package com.possystem.backend.report.dto;

import com.possystem.backend.common.enums.PaymentMethod;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonthlyReportResponse {
    YearMonth month;
    BigDecimal totalRevenue;
    BigDecimal totalRefund;
    long totalOrders;
    Map<PaymentMethod, BigDecimal> revenueByMethod;
    List<TopProductDTO> topProducts;
}
