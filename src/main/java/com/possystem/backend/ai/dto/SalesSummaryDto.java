package com.possystem.backend.ai.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SalesSummaryDto {
    LocalDate fromDate;
    LocalDate toDate;
    BigDecimal totalRevenue;
    long totalOrders;
    BigDecimal averageOrderValue;
    BigDecimal totalRefund;
    long memberCustomerCount;
    long guestCustomerCount;
    Map<String, BigDecimal> revenueByPaymentMethod;
}
