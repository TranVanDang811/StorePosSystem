package com.possystem.backend.report.dto;

import com.possystem.backend.common.enums.PaymentMethod;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DailyReportResponse {
    LocalDate date;
    BigDecimal totalRevenue;
    BigDecimal totalRefund;
    Long totalOrders;
    Map<PaymentMethod, BigDecimal> revenueByMethod;
    List<TopProductDTO> topProducts;
}
